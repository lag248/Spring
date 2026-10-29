package com.example.hotelbooking.booking.dto;

import java.time.Instant;
import java.time.LocalDate;
import com.example.hotelbooking.booking.domain.Booking;
import com.example.hotelbooking.booking.domain.BookingStatus;

public record BookingResponse(Long id, String requestId, Long userId, Long roomId,
                              LocalDate startDate, LocalDate endDate, BookingStatus status, Instant createdAt) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(booking.getId(), booking.getRequestId(), booking.getUserId(), booking.getRoomId(),
                booking.getStartDate(), booking.getEndDate(), booking.getStatus(), booking.getCreatedAt());
    }
}
