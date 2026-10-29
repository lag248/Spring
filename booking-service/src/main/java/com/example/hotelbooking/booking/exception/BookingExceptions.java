package com.example.hotelbooking.booking.exception;

public final class BookingExceptions {
    private BookingExceptions() { }
    public static class RoomUnavailableException extends RuntimeException {
        public RoomUnavailableException(String message) { super(message); }
    }
    public static class HotelUnavailableException extends RuntimeException {
        public HotelUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(String message) { super(message); }
    }
}
