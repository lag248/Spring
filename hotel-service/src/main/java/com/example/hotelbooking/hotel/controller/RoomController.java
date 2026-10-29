package com.example.hotelbooking.hotel.controller;

import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.hotelbooking.hotel.dto.AvailabilityRequest;
import com.example.hotelbooking.hotel.dto.RequestIdRequest;
import com.example.hotelbooking.hotel.dto.RoomCreateRequest;
import com.example.hotelbooking.hotel.dto.RoomResponse;
import com.example.hotelbooking.hotel.dto.RoomUpdateRequest;
import com.example.hotelbooking.hotel.security.InternalTokenValidator;
import com.example.hotelbooking.hotel.service.RoomManagementService;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomManagementService service;
    private final InternalTokenValidator internalTokenValidator;
    public RoomController(RoomManagementService service, InternalTokenValidator internalTokenValidator) {
        this.service = service; this.internalTokenValidator = internalTokenValidator;
    }

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<RoomResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long hotelId,
            @RequestParam(defaultValue = "id") String sort) {
        return service.list(startDate, endDate, false, hotelId, sort);
    }

    @GetMapping("/recommend")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<RoomResponse> recommend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long hotelId) {
        return service.list(startDate, endDate, true, hotelId, "timesBooked");
    }

    @GetMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public RoomResponse get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public RoomResponse create(@Valid @RequestBody RoomCreateRequest request) { return service.create(request); }

    @PatchMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public RoomResponse update(@PathVariable Long id, @Valid @RequestBody RoomUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) { service.delete(id); }

    @PostMapping("/{id}/confirm-availability")
    public void reserve(@PathVariable Long id, @RequestHeader(value = "X-Internal-Token", required = false) String token,
                        @Valid @RequestBody AvailabilityRequest request) {
        internalTokenValidator.requireValid(token);
        service.reserve(id, request);
    }

    @PostMapping("/{id}/commit")
    public void commit(@PathVariable Long id, @RequestHeader(value = "X-Internal-Token", required = false) String token,
                       @Valid @RequestBody RequestIdRequest request) {
        internalTokenValidator.requireValid(token);
        service.commit(id, request.requestId());
    }

    @PostMapping("/{id}/release")
    public void release(@PathVariable Long id, @RequestHeader(value = "X-Internal-Token", required = false) String token,
                        @Valid @RequestBody RequestIdRequest request) {
        internalTokenValidator.requireValid(token);
        service.release(id, request.requestId());
    }
}
