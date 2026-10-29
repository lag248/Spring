package com.example.hotelbooking.booking.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.hotelbooking.booking.domain.Booking;
import com.example.hotelbooking.booking.domain.BookingStatus;
import com.example.hotelbooking.booking.domain.UserAccount;
import com.example.hotelbooking.booking.dto.BookingCreateRequest;
import com.example.hotelbooking.booking.dto.BookingResponse;
import com.example.hotelbooking.booking.dto.RoomView;
import com.example.hotelbooking.booking.exception.BookingExceptions.IdempotencyConflictException;
import com.example.hotelbooking.booking.exception.BookingExceptions.RoomUnavailableException;

@Service
public class BookingOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(BookingOrchestrator.class);
    private final BookingTransactionService transactions;
    private final HotelClient hotelClient;
    private final RoomPlanner roomPlanner;

    public BookingOrchestrator(BookingTransactionService transactions, HotelClient hotelClient, RoomPlanner roomPlanner) {
        this.transactions = transactions; this.hotelClient = hotelClient; this.roomPlanner = roomPlanner;
    }

    /** Orchestration deliberately has no @Transactional boundary: every write is committed locally before remote work. */
    public BookingResponse create(BookingCreateRequest request, String username) {
        validateRequest(request);
        UserAccount user = transactions.findUserByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
        boolean autoSelect = Boolean.TRUE.equals(request.autoSelect());

        var existing = transactions.findByRequestId(request.requestId());
        if (existing.isPresent()) {
            ensureSameRequest(existing.get(), user.getId(), request, autoSelect);
            if (existing.get().getStatus() == BookingStatus.CANCELLED && existing.get().getRoomId() != null) {
                // If an earlier compensation attempt timed out, redelivery retries the same idempotent release.
                hotelClient.release(existing.get().getRoomId(), existing.get().getRequestId());
            }
            return BookingResponse.from(existing.get());
        }

        Booking pending;
        try {
            pending = transactions.createPending(request.requestId(), user.getId(), autoSelect ? null : request.roomId(),
                    request.startDate(), request.endDate());
        } catch (IdempotencyConflictException | DataIntegrityViolationException race) {
            Booking existingAfterRace = transactions.findByRequestId(request.requestId())
                    .orElseThrow(() -> race);
            ensureSameRequest(existingAfterRace, user.getId(), request, autoSelect);
            if (existingAfterRace.getStatus() == BookingStatus.CANCELLED && existingAfterRace.getRoomId() != null) {
                hotelClient.release(existingAfterRace.getRoomId(), existingAfterRace.getRequestId());
            }
            return BookingResponse.from(existingAfterRace);
        }
        log.info("booking_created bookingId={} requestId={} status=PENDING traceId={}",
                pending.getId(), pending.getRequestId(), org.slf4j.MDC.get("traceId"));
        return executeSaga(pending, request, autoSelect);
    }

    private BookingResponse executeSaga(Booking pending, BookingCreateRequest request, boolean autoSelect) {
        List<Long> candidates = new ArrayList<>();
        try {
            if (autoSelect) {
                List<RoomView> recommended = roomPlanner.rank(hotelClient.recommend(request.startDate(), request.endDate()));
                Set<Long> unique = new LinkedHashSet<>();
                // The room already attached to a PENDING row is tried first after a retry/recovery path.
                if (pending.getRoomId() != null) unique.add(pending.getRoomId());
                recommended.stream().filter(RoomView::available).map(RoomView::id).forEach(unique::add);
                candidates.addAll(unique);
            } else {
                if (request.roomId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roomId is required when autoSelect=false");
                candidates.add(request.roomId());
            }
        } catch (RuntimeException ex) {
            cancelLocal(pending.getId(), pending.getRequestId(), null, ex);
            throw ex;
        }

        if (candidates.isEmpty()) {
            transactions.markCancelled(pending.getId());
            log.warn("booking_compensated bookingId={} requestId={} reason=no-free-room traceId={}",
                    pending.getId(), pending.getRequestId(), org.slf4j.MDC.get("traceId"));
            throw new RoomUnavailableException("No room is available for the requested dates");
        }

        Long lastAttemptedRoom = null;
        RoomUnavailableException lastConflict = null;
        for (Long roomId : candidates) {
            lastAttemptedRoom = roomId;
            transactions.assignRoom(pending.getId(), roomId);
            try {
                hotelClient.reserve(roomId, request.requestId(), request.startDate(), request.endDate());
                hotelClient.commit(roomId, request.requestId());
                Booking confirmed = transactions.markConfirmed(pending.getId());
                log.info("booking_state_transition bookingId={} requestId={} from=PENDING to=CONFIRMED roomId={} traceId={}",
                        confirmed.getId(), confirmed.getRequestId(), roomId, org.slf4j.MDC.get("traceId"));
                return BookingResponse.from(confirmed);
            } catch (RoomUnavailableException conflict) {
                lastConflict = conflict;
                log.info("room_candidate_rejected bookingId={} requestId={} roomId={} autoSelect={}",
                        pending.getId(), pending.getRequestId(), roomId, autoSelect);
                if (!autoSelect) {
                    cancelLocal(pending.getId(), request.requestId(), roomId, conflict);
                    throw conflict;
                }
            } catch (RuntimeException failure) {
                cancelLocal(pending.getId(), request.requestId(), roomId, failure);
                if (failure instanceof ResponseStatusException) throw failure;
                throw failure;
            }
        }

        // All candidate attempts returned a conflict. Release is safe even if no hold was created.
        transactions.markCancelled(pending.getId());
        if (lastAttemptedRoom != null) {
            try {
                hotelClient.release(lastAttemptedRoom, request.requestId());
            } catch (RuntimeException releaseFailure) {
                log.error("booking_compensation_failed bookingId={} requestId={} roomId={} traceId={}",
                        pending.getId(), request.requestId(), lastAttemptedRoom, org.slf4j.MDC.get("traceId"), releaseFailure);
                throw releaseFailure;
            }
        }
        log.warn("booking_state_transition bookingId={} requestId={} from=PENDING to=CANCELLED reason=all-rooms-conflicted traceId={}",
                pending.getId(), pending.getRequestId(), org.slf4j.MDC.get("traceId"));
        throw lastConflict == null ? new RoomUnavailableException("No room is available for the requested dates") : lastConflict;
    }

    public BookingResponse get(Long id, String username, boolean admin) {
        Booking booking = transactions.getById(id);
        UserAccount user = transactions.findUserByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
        if (!admin && !Objects.equals(booking.getUserId(), user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can access only your own bookings");
        }
        return BookingResponse.from(booking);
    }

    public Page<BookingResponse> history(String username, boolean admin, BookingStatus status, Pageable pageable) {
        if (admin) return transactions.allBookings(status, pageable).map(BookingResponse::from);
        UserAccount user = transactions.findUserByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
        return transactions.history(user.getId(), status, pageable).map(BookingResponse::from);
    }

    public Page<BookingResponse> history(String username, boolean admin, Pageable pageable) {
        return history(username, admin, null, pageable);
    }

    public BookingResponse cancel(Long id, String username, boolean admin) {
        Booking booking = transactions.getById(id);
        UserAccount user = transactions.findUserByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
        if (!admin && !Objects.equals(booking.getUserId(), user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can cancel only your own bookings");
        }
        if (booking.getStatus() == BookingStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking is still being processed; retry after it reaches a final state");
        }
        if (booking.getRoomId() == null) return BookingResponse.from(booking);
        if (booking.getStatus() != BookingStatus.CANCELLED) {
            booking = transactions.markCancelled(id);
            log.info("booking_state_transition bookingId={} requestId={} from=CONFIRMED to=CANCELLED traceId={}",
                    booking.getId(), booking.getRequestId(), org.slf4j.MDC.get("traceId"));
        }
        // Calling release also for a repeated DELETE makes a failed compensation retryable and idempotent.
        hotelClient.release(booking.getRoomId(), booking.getRequestId());
        return BookingResponse.from(transactions.getById(id));
    }

    private void cancelLocal(Long bookingId, String requestId, Long roomId, RuntimeException cause) {
        try {
            Booking cancelled = transactions.markCancelled(bookingId);
            log.warn("booking_state_transition bookingId={} requestId={} from=PENDING to=CANCELLED cause={} traceId={}",
                    bookingId, requestId, cause.getClass().getSimpleName(), org.slf4j.MDC.get("traceId"));
        } catch (RuntimeException localFailure) {
            log.error("booking_local_compensation_failed bookingId={} requestId={} traceId={}",
                    bookingId, requestId, org.slf4j.MDC.get("traceId"), localFailure);
            throw localFailure;
        }
        if (roomId != null) hotelClient.release(roomId, requestId);
    }

    private void validateRequest(BookingCreateRequest request) {
        if (request.startDate() == null || request.endDate() == null || !request.endDate().isAfter(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must be after startDate");
        }
        if (request.startDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate cannot be in the past");
        }
        if (!Boolean.TRUE.equals(request.autoSelect()) && request.roomId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roomId is required when autoSelect=false");
        }
    }

    private void ensureSameRequest(Booking existing, Long userId, BookingCreateRequest request, boolean autoSelect) {
        boolean roomMatches = autoSelect || existing.getRoomId() == null || Objects.equals(existing.getRoomId(), request.roomId());
        if (!Objects.equals(existing.getUserId(), userId) || !existing.getStartDate().equals(request.startDate())
                || !existing.getEndDate().equals(request.endDate()) || !roomMatches) {
            throw new IdempotencyConflictException("requestId was already used with different booking data");
        }
    }
}
