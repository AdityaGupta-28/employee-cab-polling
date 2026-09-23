package com.cabpooling.employeecabpooling.controller;

import com.cabpooling.employeecabpooling.dto.cab.CabRequest;
import com.cabpooling.employeecabpooling.dto.cab.CabResponse;
import com.cabpooling.employeecabpooling.dto.cab.CabStatusRequest;
import com.cabpooling.employeecabpooling.service.CabService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cabs")
@RequiredArgsConstructor
public class CabController {

    private final CabService cabService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<CabResponse> create(@Valid @RequestBody CabRequest request) {
        return ResponseEntity.ok(cabService.create(request));
    }

    /**
     * List all cabs. Pass ?active=true to filter only active cabs.
     */
    @GetMapping
    public ResponseEntity<List<CabResponse>> findAll(
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(cabService.findAll(active));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CabResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(cabService.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<CabResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody CabRequest request) {
        return ResponseEntity.ok(cabService.update(id, request));
    }

    /**
     * Toggle a cab's active/inactive status.
     * PATCH /api/cabs/{id}/status  body: { "isActive": false }
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<CabResponse> updateStatus(@PathVariable Long id,
                                                     @Valid @RequestBody CabStatusRequest request) {
        return ResponseEntity.ok(cabService.updateStatus(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        cabService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
