package com.example.hotelbooking.booking.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.hotelbooking.booking.dto.AuthRequest;
import com.example.hotelbooking.booking.dto.AuthResponse;
import com.example.hotelbooking.booking.dto.RegisterRequest;
import com.example.hotelbooking.booking.service.AuthService;

@RestController
@RequestMapping("/api/user")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) { return service.register(request); }

    @PostMapping("/auth")
    public AuthResponse authenticate(@Valid @RequestBody AuthRequest request) { return service.authenticate(request); }
}
