package com.example.hotelbooking.booking.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.example.hotelbooking.booking.dto.BookingCreateRequest;
import com.example.hotelbooking.booking.dto.BookingResponse;
import com.example.hotelbooking.booking.domain.BookingStatus;
import com.example.hotelbooking.booking.service.BookingOrchestrator;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api")
public class BookingController {
    private final BookingOrchestrator orchestrator;
    public BookingController(BookingOrchestrator orchestrator) { this.orchestrator = orchestrator; }

    @PostMapping({"/booking", "/bookings"})
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public BookingResponse create(@Valid @RequestBody BookingCreateRequest request, Authentication authentication,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank() && !idempotencyKey.equals(request.requestId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Idempotency-Key must match body requestId");
        }
        return orchestrator.create(request, authentication.getName());
    }

    @GetMapping("/bookings")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public Page<BookingResponse> history(Authentication authentication,
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orchestrator.history(authentication.getName(), isAdmin(authentication), status, pageable);
    }

    @GetMapping({"/booking/{id}", "/bookings/{id}"})
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public BookingResponse get(@PathVariable Long id, Authentication authentication) {
        return orchestrator.get(id, authentication.getName(), isAdmin(authentication));
    }

    @DeleteMapping({"/booking/{id}", "/bookings/{id}"})
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public BookingResponse cancel(@PathVariable Long id, Authentication authentication) {
        return orchestrator.cancel(id, authentication.getName(), isAdmin(authentication));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
