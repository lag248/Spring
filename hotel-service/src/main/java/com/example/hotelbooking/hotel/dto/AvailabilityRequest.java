package com.example.hotelbooking.hotel.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AvailabilityRequest(@NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._-]+") String requestId, @NotNull LocalDate startDate,
                                  @NotNull LocalDate endDate) { }
