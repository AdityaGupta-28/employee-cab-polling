package com.cabpooling.employeecabpooling.controller;

import com.cabpooling.employeecabpooling.dto.allocation.AutoClusterRequest;
import com.cabpooling.employeecabpooling.dto.allocation.AutoClusterResponse;
import com.cabpooling.employeecabpooling.dto.assignment.CabAssignmentResponse;
import com.cabpooling.employeecabpooling.service.AllocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/allocations")
@RequiredArgsConstructor
public class AllocationController {

    private final AllocationService allocationService;
    private final com.cabpooling.employeecabpooling.service.ReplanningService replanningService;

    @PostMapping("/auto-cluster")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<AutoClusterResponse> autoCluster(
            @Valid @RequestBody AutoClusterRequest request) {
        AutoClusterResponse response = allocationService.autoCluster(request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/optimize/{cabAssignmentId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<CabAssignmentResponse> optimizeAssignment(
            @PathVariable Long cabAssignmentId) {
        CabAssignmentResponse response = allocationService.optimizeAssignment(cabAssignmentId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/insert-booking/{bookingId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<com.cabpooling.employeecabpooling.dto.replanning.LateBookingInsertResponse> insertLateBooking(
            @PathVariable Long bookingId) {
        com.cabpooling.employeecabpooling.dto.replanning.LateBookingInsertResponse response =
                replanningService.insertLateBooking(bookingId);
        return ResponseEntity.ok(response);
    }
}
