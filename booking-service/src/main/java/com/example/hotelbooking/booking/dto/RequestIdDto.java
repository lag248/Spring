package com.example.hotelbooking.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RequestIdDto(@NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._-]+") String requestId) { }
