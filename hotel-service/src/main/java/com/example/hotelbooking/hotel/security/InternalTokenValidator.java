package com.example.hotelbooking.hotel.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class InternalTokenValidator {
    private final byte[] expected;
    public InternalTokenValidator(@Value("${internal.service-token}") String token) {
        this.expected = token.getBytes(StandardCharsets.UTF_8);
    }
    public void requireValid(String actual) {
        if (actual == null || !MessageDigest.isEqual(expected, actual.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal service token");
        }
    }
}
