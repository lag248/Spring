package com.example.hotelbooking.booking.service;

import java.util.Optional;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.example.hotelbooking.booking.domain.Booking;
import com.example.hotelbooking.booking.domain.BookingStatus;
import com.example.hotelbooking.booking.domain.UserAccount;
import com.example.hotelbooking.booking.repository.BookingRepository;
import com.example.hotelbooking.booking.repository.UserRepository;

/** Each method is a separate local transaction. Do not wrap the orchestration in one DB transaction. */
@Service
public class BookingTransactionService {
    private final BookingRepository bookings;
    private final UserRepository users;

    public BookingTransactionService(BookingRepository bookings, UserRepository users) {
        this.bookings = bookings; this.users = users;
    }

    @Transactional(readOnly = true)
    public Optional<Booking> findByRequestId(String requestId) { return bookings.findByRequestId(requestId); }

    @Transactional(readOnly = true)
    public Optional<UserAccount> findUserByUsername(String username) { return users.findByUsername(username); }

    @Transactional
    public Booking createPending(String requestId, Long userId, Long roomId,
                                 java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (bookings.findByRequestId(requestId).isPresent()) {
            throw new com.example.hotelbooking.booking.exception.BookingExceptions.IdempotencyConflictException("requestId already exists");
        }
        return bookings.saveAndFlush(new Booking(requestId, userId, roomId, startDate, endDate));
    }

    @Transactional
    public Booking assignRoom(Long bookingId, Long roomId) {
        Booking booking = findBooking(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) return booking;
        booking.setRoomId(roomId);
        return bookings.saveAndFlush(booking);
    }

    @Transactional
    public Booking markConfirmed(Long bookingId) {
        Booking booking = findBooking(bookingId);
        if (booking.getStatus() == BookingStatus.CONFIRMED) return booking;
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only PENDING bookings can be confirmed");
        }
        if (booking.getRoomId() == null) throw new IllegalStateException("Cannot confirm a booking without roomId");
        booking.setStatus(BookingStatus.CONFIRMED);
        return bookings.saveAndFlush(booking);
    }

    @Transactional
    public Booking markCancelled(Long bookingId) {
        Booking booking = findBooking(bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED) return booking;
        booking.setStatus(BookingStatus.CANCELLED);
        return bookings.saveAndFlush(booking);
    }

    @Transactional(readOnly = true)
    public Booking getById(Long bookingId) { return findBooking(bookingId); }

    @Transactional(readOnly = true)
    public Page<Booking> history(Long userId, Pageable pageable) {
        return bookings.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Booking> history(Long userId, BookingStatus status, Pageable pageable) {
        return status == null ? history(userId, pageable)
                : bookings.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Booking> allBookings(Pageable pageable) { return bookings.findAll(pageable); }

    @Transactional(readOnly = true)
    public Page<Booking> allBookings(BookingStatus status, Pageable pageable) {
        return status == null ? allBookings(pageable)
                : bookings.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    @Transactional(readOnly = true)
    public List<Booking> stalePendingBookings(Instant cutoff) {
        return bookings.findByStatusAndCreatedAtBefore(BookingStatus.PENDING, cutoff);
    }

    private Booking findBooking(Long id) {
        return bookings.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found: " + id));
    }
}
