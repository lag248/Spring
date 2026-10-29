package com.example.hotelbooking.hotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HotelRequest(@NotBlank @Size(max = 150) String name,
                           @NotBlank @Size(max = 300) String address) { }
