package com.example.hotelbooking.gateway.config;

import java.time.Instant;

/** Stable error contract returned by the edge service for rejected requests. */
public record ApiError(Instant timestamp, int status, String error, String message,
                       String path, String traceId) { }
