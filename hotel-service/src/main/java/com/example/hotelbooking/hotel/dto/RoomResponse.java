package com.example.hotelbooking.hotel.dto;

import com.example.hotelbooking.hotel.domain.Room;

public record RoomResponse(Long id, Long hotelId, String hotelName, String number,
                           boolean available, long timesBooked) {
    public static RoomResponse from(Room room) {
        return new RoomResponse(room.getId(), room.getHotel().getId(), room.getHotel().getName(),
                room.getNumber(), room.isAvailable(), room.getTimesBooked());
    }
}
