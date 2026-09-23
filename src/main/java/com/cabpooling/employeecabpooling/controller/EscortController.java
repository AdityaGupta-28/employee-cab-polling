package com.cabpooling.employeecabpooling.controller;

import com.cabpooling.employeecabpooling.dto.assignment.EscortRequest;
import com.cabpooling.employeecabpooling.dto.assignment.EscortResponse;
import com.cabpooling.employeecabpooling.dto.assignment.EscortStatusRequest;
import com.cabpooling.employeecabpooling.service.EscortService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/escorts")
@RequiredArgsConstructor
public class EscortController {

    private final EscortService escortService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<EscortResponse> createEscort(@Valid @RequestBody EscortRequest request) {
        EscortResponse response = escortService.create(request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<EscortResponse>> listEscorts() {
        List<EscortResponse> responses = escortService.findAll();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<EscortResponse> getEscortById(@PathVariable Long id) {
        EscortResponse response = escortService.findById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/assignment/{cabAssignmentId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<EscortResponse> getEscortByCabAssignmentId(@PathVariable Long cabAssignmentId) {
        EscortResponse response = escortService.findByCabAssignmentId(cabAssignmentId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<EscortResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody EscortStatusRequest request) {
        EscortResponse response = escortService.updateStatus(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteEscort(@PathVariable Long id) {
        escortService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
