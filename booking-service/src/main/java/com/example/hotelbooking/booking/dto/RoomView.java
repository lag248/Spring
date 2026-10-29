package com.example.hotelbooking.booking.dto;

public record RoomView(Long id, Long hotelId, String hotelName, String number,
                       boolean available, long timesBooked) { }
