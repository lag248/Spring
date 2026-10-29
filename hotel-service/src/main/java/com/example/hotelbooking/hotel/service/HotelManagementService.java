package com.example.hotelbooking.hotel.service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.example.hotelbooking.hotel.domain.Hotel;
import com.example.hotelbooking.hotel.domain.HoldStatus;
import com.example.hotelbooking.hotel.domain.Room;
import com.example.hotelbooking.hotel.dto.HotelRequest;
import com.example.hotelbooking.hotel.dto.HotelResponse;
import com.example.hotelbooking.hotel.dto.OccupancyResponse;
import com.example.hotelbooking.hotel.repository.HotelRepository;
import com.example.hotelbooking.hotel.repository.RoomHoldRepository;
import com.example.hotelbooking.hotel.repository.RoomRepository;

@Service
public class HotelManagementService {
    private static final List<HoldStatus> ACTIVE = List.of(HoldStatus.HELD, HoldStatus.CONFIRMED);
    private final HotelRepository hotels;
    private final RoomRepository rooms;
    private final RoomHoldRepository holds;

    public HotelManagementService(HotelRepository hotels, RoomRepository rooms, RoomHoldRepository holds) {
        this.hotels = hotels; this.rooms = rooms; this.holds = holds;
    }

    @Transactional(readOnly = true)
    public List<HotelResponse> list(String name, String address) {
        String nameFilter = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        String addressFilter = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
        return hotels.findAll().stream()
                .filter(hotel -> nameFilter.isBlank() || hotel.getName().toLowerCase(Locale.ROOT).contains(nameFilter))
                .filter(hotel -> addressFilter.isBlank() || hotel.getAddress().toLowerCase(Locale.ROOT).contains(addressFilter))
                .sorted(java.util.Comparator.comparing(Hotel::getName).thenComparing(Hotel::getId))
                .map(HotelResponse::from).toList();
    }

    public List<HotelResponse> list() { return list(null, null); }

    @Transactional(readOnly = true)
    public HotelResponse get(Long id) { return HotelResponse.from(findHotel(id)); }

    @Transactional
    public HotelResponse create(HotelRequest request) {
        return HotelResponse.from(hotels.save(new Hotel(request.name().trim(), request.address().trim())));
    }

    @Transactional
    public HotelResponse update(Long id, HotelRequest request) {
        Hotel hotel = findHotel(id);
        hotel.setName(request.name().trim());
        hotel.setAddress(request.address().trim());
        return HotelResponse.from(hotels.save(hotel));
    }

    @Transactional
    public void delete(Long id) {
        Hotel hotel = findHotel(id);
        if (rooms.existsByHotel_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Delete rooms belonging to the hotel first");
        }
        hotels.delete(hotel);
    }

    @Transactional(readOnly = true)
    public OccupancyResponse occupancy(Long hotelId, LocalDate requestedStart, LocalDate requestedEnd) {
        findHotel(hotelId);
        LocalDate start = requestedStart == null ? LocalDate.now() : requestedStart;
        LocalDate end = requestedEnd == null ? start.plusDays(30) : requestedEnd;
        if (!end.isAfter(start)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must be after startDate");
        List<Room> hotelRooms = rooms.findByHotel_IdOrderByNumberAsc(hotelId);
        long roomsWithBookings = 0;
        long activeBookings = 0;
        for (Room room : hotelRooms) {
            long count = holds.countByRoom_IdAndStatusInAndStartDateLessThanAndEndDateGreaterThan(
                    room.getId(), ACTIVE, end, start);
            if (count > 0) roomsWithBookings++;
            activeBookings += count;
        }
        double percent = hotelRooms.isEmpty() ? 0.0 : Math.round((roomsWithBookings * 10000.0) / hotelRooms.size()) / 100.0;
        return new OccupancyResponse(hotelId, start, end, hotelRooms.size(), roomsWithBookings, activeBookings, percent);
    }

    private Hotel findHotel(Long id) {
        return hotels.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found: " + id));
    }
}
