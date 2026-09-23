package com.cabpooling.employeecabpooling.controller;

import com.cabpooling.employeecabpooling.dto.booking.BookingRequest;
import com.cabpooling.employeecabpooling.dto.booking.BookingResponse;
import com.cabpooling.employeecabpooling.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /**
     * Create a booking for the authenticated employee.
     * POST /api/bookings
     */
    @PostMapping
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request) {
        return ResponseEntity.ok(bookingService.create(request));
    }

    /**
     * Get the authenticated employee's own bookings.
     * GET /api/bookings/my?date=2025-12-01&page=0&size=10
     */
    @GetMapping("/my")
    public ResponseEntity<Page<BookingResponse>> findMyBookings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Pageable pageable) {
        return ResponseEntity.ok(bookingService.findMyBookings(date, pageable));
    }

    /**
     * Get a specific booking by ID. Admin can view any; employee can only view own.
     */
    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.findById(id));
    }

    /**
     * Cancel a booking. Only the booking owner can cancel.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<BookingResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancel(id));
    }

    /**
     * Admin: view all bookings across all employees.
     * GET /api/bookings?page=0&size=20
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Page<BookingResponse>> findAll(Pageable pageable) {
        return ResponseEntity.ok(bookingService.findAll(pageable));
    }
}
