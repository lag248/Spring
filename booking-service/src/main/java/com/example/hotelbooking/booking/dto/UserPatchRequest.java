package com.example.hotelbooking.booking.dto;

import com.example.hotelbooking.booking.domain.Role;
import jakarta.validation.constraints.Size;

public record UserPatchRequest(@Size(min = 3, max = 80) String username,
                               @Size(min = 8, max = 72) String password,
                               Role role) { }
