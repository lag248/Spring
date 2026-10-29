package com.example.hotelbooking.booking.dto;

public record AuthResponse(String tokenType, String accessToken, long expiresInSeconds,
                           Long userId, String username, String role) { }
