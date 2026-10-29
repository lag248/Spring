package com.example.hotelbooking.hotel.dto;

import com.example.hotelbooking.hotel.domain.Hotel;

public record HotelResponse(Long id, String name, String address) {
    public static HotelResponse from(Hotel hotel) {
        return new HotelResponse(hotel.getId(), hotel.getName(), hotel.getAddress());
    }
}
