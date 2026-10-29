package com.example.hotelbooking.hotel.dto;

import jakarta.validation.constraints.Size;

public record RoomUpdateRequest(@Size(max = 30) String number, Boolean available) { }
