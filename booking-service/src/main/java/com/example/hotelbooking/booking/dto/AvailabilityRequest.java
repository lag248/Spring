package com.example.hotelbooking.booking.dto;

import java.time.LocalDate;

public record AvailabilityRequest(String requestId, LocalDate startDate, LocalDate endDate) { }
