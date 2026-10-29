package com.example.hotelbooking.booking.dto;

import com.example.hotelbooking.booking.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserAdminRequest(@NotBlank @Size(min = 3, max = 80) String username,
                               @Size(min = 8, max = 72) String password,
                               @NotNull Role role) { }
