package com.example.hotelbooking.hotel.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.hotelbooking.hotel.domain.HoldStatus;
import com.example.hotelbooking.hotel.domain.Hotel;
import com.example.hotelbooking.hotel.domain.Room;
import com.example.hotelbooking.hotel.domain.RoomHold;
import com.example.hotelbooking.hotel.dto.AvailabilityRequest;
import com.example.hotelbooking.hotel.dto.RoomCreateRequest;
import com.example.hotelbooking.hotel.dto.RoomResponse;
import com.example.hotelbooking.hotel.dto.RoomUpdateRequest;
import com.example.hotelbooking.hotel.repository.HotelRepository;
import com.example.hotelbooking.hotel.repository.RoomHoldRepository;
import com.example.hotelbooking.hotel.repository.RoomRepository;

@Service
public class RoomManagementService {
    private static final Logger log = LoggerFactory.getLogger(RoomManagementService.class);
    private static final List<HoldStatus> ACTIVE = List.of(HoldStatus.HELD, HoldStatus.CONFIRMED);
    private final RoomRepository rooms;
    private final HotelRepository hotels;
    private final RoomHoldRepository holds;
    /** Serializes duplicate deliveries with the same idempotency key in this service instance. */
    private final ConcurrentHashMap<String, RequestLock> requestLocks = new ConcurrentHashMap<>();

    public RoomManagementService(RoomRepository rooms, HotelRepository hotels, RoomHoldRepository holds) {
        this.rooms = rooms; this.hotels = hotels; this.holds = holds;
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> list(LocalDate requestedStart, LocalDate requestedEnd, boolean recommended,
                                   Long hotelId, String sort) {
        DateRange range = range(requestedStart, requestedEnd);
        List<Room> source = new ArrayList<>(rooms.findByAvailableTrueOrderByTimesBookedAscIdAsc());
        List<Room> free = source.stream()
                .filter(room -> hotelId == null || room.getHotel().getId().equals(hotelId))
                .filter(room -> !isOccupied(room.getId(), range.start(), range.end())).toList();
        Comparator<Room> order = comparator(recommended, sort);
        return free.stream().sorted(order).map(RoomResponse::from).toList();
    }

    public List<RoomResponse> list(LocalDate requestedStart, LocalDate requestedEnd, boolean recommended) {
        return list(requestedStart, requestedEnd, recommended, null, null);
    }

    private Comparator<Room> comparator(boolean recommended, String sort) {
        if (recommended || sort == null || sort.isBlank() || "timesBooked".equals(sort)) {
            return Comparator.comparingLong(Room::getTimesBooked).thenComparing(Room::getId);
        }
        if ("number".equals(sort)) return Comparator.comparing(Room::getNumber).thenComparing(Room::getId);
        if ("id".equals(sort)) return Comparator.comparing(Room::getId);
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sort must be one of: id, number, timesBooked");
    }

    @Transactional(readOnly = true)
    public RoomResponse get(Long id) { return RoomResponse.from(findRoom(id)); }

    @Transactional
    public RoomResponse create(RoomCreateRequest request) {
        Hotel hotel = hotels.findById(request.hotelId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found: " + request.hotelId()));
        if (rooms.existsByHotel_IdAndNumber(hotel.getId(), request.number().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room number already exists in this hotel");
        }
        Room room = new Room(hotel, request.number().trim(), request.available() == null || request.available());
        return RoomResponse.from(rooms.save(room));
    }

    @Transactional
    public RoomResponse update(Long id, RoomUpdateRequest request) {
        if ((request.number() == null || request.number().isBlank()) && request.available() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one room field must be provided");
        }
        Room room = rooms.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found: " + id));
        if (request.number() != null && !request.number().isBlank()) {
            if (!request.number().trim().equals(room.getNumber())
                    && rooms.existsByHotel_IdAndNumber(room.getHotel().getId(), request.number().trim())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Room number already exists in this hotel");
            }
            room.setNumber(request.number().trim());
        }
        if (request.available() != null) room.setAvailable(request.available());
        return RoomResponse.from(rooms.save(room));
    }

    @Transactional
    public void delete(Long id) {
        Room room = rooms.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found: " + id));
        if (holds.existsByRoom_IdAndStatusIn(id, ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room has active reservations and cannot be deleted");
        }
        if (holds.existsByRoom_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room has booking history and cannot be deleted");
        }
        rooms.delete(room);
    }

    @Transactional
    public void reserve(Long roomId, AvailabilityRequest request) {
        RequestLock requestLock = requestLocks.compute(request.requestId(), (key, current) -> {
            if (current == null) return new RequestLock();
            current.users().incrementAndGet();
            return current;
        });
        try {
            synchronized (requestLock.monitor()) {
                reserveUnderRequestLock(roomId, request);
            }
        } finally {
            requestLocks.computeIfPresent(request.requestId(), (key, current) ->
                    current.users().decrementAndGet() == 0 ? null : current);
        }
    }

    private void reserveUnderRequestLock(Long roomId, AvailabilityRequest request) {
        validateRequest(request.requestId(), request.startDate(), request.endDate());
        Optional<RoomHold> existing = holds.findByRequestId(request.requestId());
        if (existing.isPresent()) {
            RoomHold hold = existing.get();
            if (!hold.getRoom().getId().equals(roomId) || !hold.getStartDate().equals(request.startDate())
                    || !hold.getEndDate().equals(request.endDate())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "requestId was already used with different reservation data");
            }
            if (hold.getStatus() == HoldStatus.RELEASED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "requestId was already released and cannot be reused");
            }
            return; // Duplicate delivery: same hold, no side effect.
        }
        Room room = rooms.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found: " + roomId));
        // Recheck under the room lock; this closes the duplicate-delivery race between the first lookup and lock acquisition.
        Optional<RoomHold> concurrentDuplicate = holds.findByRequestId(request.requestId());
        if (concurrentDuplicate.isPresent()) {
            RoomHold hold = concurrentDuplicate.get();
            if (hold.getRoom().getId().equals(roomId) && hold.getStartDate().equals(request.startDate())
                    && hold.getEndDate().equals(request.endDate()) && hold.getStatus() != HoldStatus.RELEASED) return;
            throw new ResponseStatusException(HttpStatus.CONFLICT, "requestId was already used with different reservation data");
        }
        if (!room.isAvailable()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is out of service");
        if (isOccupied(roomId, request.startDate(), request.endDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is already reserved for the requested dates");
        }
        holds.saveAndFlush(new RoomHold(request.requestId(), room, request.startDate(), request.endDate()));
        log.info("room_hold_created roomId={} requestId={} startDate={} endDate={}", roomId, request.requestId(), request.startDate(), request.endDate());
    }

    @Transactional
    public void commit(Long roomId, String requestId) {
        RoomHold firstRead = holds.findByRequestId(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation hold not found"));
        if (!firstRead.getRoom().getId().equals(roomId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "requestId belongs to another room");
        }
        Room room = rooms.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found: " + roomId));
        RoomHold hold = holds.findByIdForUpdate(firstRead.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation hold not found"));
        if (hold.getStatus() == HoldStatus.CONFIRMED) return; // Commit is idempotent.
        if (hold.getStatus() != HoldStatus.HELD) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Released hold cannot be committed");
        }
        hold.setStatus(HoldStatus.CONFIRMED);
        room.incrementTimesBooked();
        holds.save(hold);
        rooms.save(room);
        log.info("room_hold_committed roomId={} requestId={} timesBooked={}", roomId, requestId, room.getTimesBooked());
    }

    @Transactional
    public void release(Long roomId, String requestId) {
        Optional<RoomHold> firstReadOpt = holds.findByRequestId(requestId);
        if (firstReadOpt.isEmpty()) {
            log.info("room_hold_release_noop roomId={} requestId={} reason=not-found", roomId, requestId);
            return; // Compensation is idempotent even when the original response was lost.
        }
        RoomHold firstRead = firstReadOpt.get();
        if (!firstRead.getRoom().getId().equals(roomId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "requestId belongs to another room");
        }
        Room room = rooms.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found: " + roomId));
        RoomHold hold = holds.findByIdForUpdate(firstRead.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation hold not found"));
        if (hold.getStatus() == HoldStatus.RELEASED) return;
        HoldStatus previousStatus = hold.getStatus();
        if (previousStatus == HoldStatus.CONFIRMED) room.decrementTimesBooked();
        hold.setStatus(HoldStatus.RELEASED);
        holds.save(hold);
        rooms.save(room);
        log.info("room_hold_released roomId={} requestId={} previousStatus={}", roomId, requestId, previousStatus);
    }

    private boolean isOccupied(Long roomId, LocalDate start, LocalDate end) {
        return holds.existsByRoom_IdAndStatusInAndStartDateLessThanAndEndDateGreaterThan(roomId, ACTIVE, end, start);
    }

    private Room findRoom(Long id) {
        return rooms.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found: " + id));
    }

    private DateRange range(LocalDate start, LocalDate end) {
        LocalDate from = start == null ? LocalDate.now() : start;
        LocalDate to = end == null ? from.plusDays(1) : end;
        validateRequest("query", from, to);
        return new DateRange(from, to);
    }

    private void validateRequest(String requestId, LocalDate start, LocalDate end) {
        if (requestId == null || requestId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestId is required");
        if (start == null || end == null || !end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must be after startDate");
        }
        if (start.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate cannot be in the past");
        }
    }

    private record DateRange(LocalDate start, LocalDate end) { }

    private record RequestLock(Object monitor, AtomicInteger users) {
        private RequestLock() { this(new Object(), new AtomicInteger(1)); }
    }
}
