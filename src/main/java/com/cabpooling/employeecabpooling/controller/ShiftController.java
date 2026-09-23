package com.cabpooling.employeecabpooling.controller;

import com.cabpooling.employeecabpooling.dto.shift.ShiftRequest;
import com.cabpooling.employeecabpooling.dto.shift.ShiftResponse;
import com.cabpooling.employeecabpooling.service.ShiftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shifts")
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftService shiftService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ShiftResponse> create(@Valid @RequestBody ShiftRequest request) {
        return ResponseEntity.ok(shiftService.create(request));
    }

    /**
     * List all shifts. Filter by office: GET /api/shifts?officeId=1
     */
    @GetMapping
    public ResponseEntity<List<ShiftResponse>> findAll(
            @RequestParam(required = false) Long officeId) {
        return ResponseEntity.ok(shiftService.findAll(officeId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShiftResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(shiftService.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ShiftResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody ShiftRequest request) {
        return ResponseEntity.ok(shiftService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        shiftService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
