package com.example.hotelbooking.hotel.controller;

import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.hotelbooking.hotel.dto.HotelRequest;
import com.example.hotelbooking.hotel.dto.HotelResponse;
import com.example.hotelbooking.hotel.dto.OccupancyResponse;
import com.example.hotelbooking.hotel.service.HotelManagementService;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/hotels")
public class HotelController {
    private final HotelManagementService service;
    public HotelController(HotelManagementService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<HotelResponse> list(@RequestParam(required = false) String name,
                                    @RequestParam(required = false) String address) {
        return service.list(name, address);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public HotelResponse get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public HotelResponse create(@Valid @RequestBody HotelRequest request) { return service.create(request); }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public HotelResponse update(@PathVariable Long id, @Valid @RequestBody HotelRequest request) { return service.update(id, request); }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) { service.delete(id); }

    @GetMapping("/{id}/occupancy")
    @PreAuthorize("hasRole('ADMIN')")
    public OccupancyResponse occupancy(@PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return service.occupancy(id, startDate, endDate);
    }
}
