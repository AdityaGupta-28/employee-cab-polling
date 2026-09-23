package com.cabpooling.employeecabpooling.controller;

import com.cabpooling.employeecabpooling.dto.employee.EmployeeResponse;
import com.cabpooling.employeecabpooling.dto.employee.EmployeeUpdateRequest;
import com.cabpooling.employeecabpooling.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    /**
     * List all employees — admin only, paginated.
     * Example: GET /api/employees?page=0&size=20&sort=createdAt,desc
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Page<EmployeeResponse>> findAll(Pageable pageable) {
        return ResponseEntity.ok(employeeService.findAll(pageable));
    }

    /**
     * Get any employee by ID. Admin can fetch anyone; employees can only fetch themselves.
     * Self-check is enforced in the service layer.
     */
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.findById(id));
    }

    /**
     * Update profile. Admin can update anyone; employees can only update themselves.
     * Self-check is enforced in the service layer.
     */
    @PutMapping("/{id}")
    public ResponseEntity<EmployeeResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody EmployeeUpdateRequest request) {
        return ResponseEntity.ok(employeeService.update(id, request));
    }

    /**
     * Hard delete an employee — admin only.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
