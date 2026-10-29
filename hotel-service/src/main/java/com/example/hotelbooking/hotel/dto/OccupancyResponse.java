package com.example.hotelbooking.hotel.dto;

import java.time.LocalDate;

public record OccupancyResponse(Long hotelId, LocalDate startDate, LocalDate endDate,
                                long totalRooms, long roomsWithBookings, long activeBookings,
                                double occupiedRoomPercent) { }
