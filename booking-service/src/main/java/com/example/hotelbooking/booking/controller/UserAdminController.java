package com.example.hotelbooking.booking.controller;

import java.util.List;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.hotelbooking.booking.dto.UserAdminRequest;
import com.example.hotelbooking.booking.dto.UserPatchRequest;
import com.example.hotelbooking.booking.dto.UserResponse;
import com.example.hotelbooking.booking.service.AuthService;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/user")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {
    private final AuthService service;
    public UserAdminController(AuthService service) { this.service = service; }

    @GetMapping
    public List<UserResponse> list() { return service.listUsers(); }

    @PostMapping
    public UserResponse create(@Valid @RequestBody UserAdminRequest request) { return service.createByAdmin(request); }

    @PatchMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserPatchRequest request) {
        return service.updateByAdmin(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) { service.deleteByAdmin(id); }
}
