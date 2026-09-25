package com.cabpooling.employeecabpooling.controller;

import com.cabpooling.employeecabpooling.dto.assignment.AssignmentStatusRequest;
import com.cabpooling.employeecabpooling.dto.assignment.CabAssignmentRequest;
import com.cabpooling.employeecabpooling.dto.assignment.CabAssignmentResponse;
import com.cabpooling.employeecabpooling.service.CabAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class CabAssignmentController {

    private final CabAssignmentService cabAssignmentService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<CabAssignmentResponse> createAssignment(@Valid @RequestBody CabAssignmentRequest request) {
        CabAssignmentResponse response = cabAssignmentService.create(request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<CabAssignmentResponse>> listAssignments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long shiftId) {
        List<CabAssignmentResponse> responses = cabAssignmentService.findAll(date, shiftId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CabAssignmentResponse>> myAssignments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(cabAssignmentService.findMyAssignments(date));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CabAssignmentResponse> getAssignmentById(@PathVariable Long id) {
        CabAssignmentResponse response = cabAssignmentService.findById(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<CabAssignmentResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody AssignmentStatusRequest request) {
        CabAssignmentResponse response = cabAssignmentService.updateStatus(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteAssignment(@PathVariable Long id) {
        cabAssignmentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/build-route")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<CabAssignmentResponse> buildRoute(@PathVariable Long id) {
        CabAssignmentResponse response = cabAssignmentService.buildRoute(id);
        return ResponseEntity.ok(response);
    }
}
