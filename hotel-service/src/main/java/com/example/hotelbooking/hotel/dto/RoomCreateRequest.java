package com.example.hotelbooking.hotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoomCreateRequest(@NotNull Long hotelId, @NotBlank @Size(max = 30) String number,
                                Boolean available) { }
