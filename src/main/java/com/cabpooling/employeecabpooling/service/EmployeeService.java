package com.cabpooling.employeecabpooling.service;

import com.cabpooling.employeecabpooling.dto.employee.EmployeeResponse;
import com.cabpooling.employeecabpooling.dto.employee.EmployeeUpdateRequest;
import com.cabpooling.employeecabpooling.exception.AccessForbiddenException;
import com.cabpooling.employeecabpooling.exception.ResourceNotFoundException;
import com.cabpooling.employeecabpooling.model.entity.Employee;
import com.cabpooling.employeecabpooling.model.enums.Role;
import com.cabpooling.employeecabpooling.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public Page<EmployeeResponse> findAll(Pageable pageable) {
        return employeeRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse findById(Long id) {
        Employee caller = getCaller();
        Employee target = getOrThrow(id);

        if (!isAdmin(caller) && !caller.getId().equals(target.getId())) {
            throw new AccessForbiddenException("You can only view your own profile");
        }
        return toResponse(target);
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeUpdateRequest request) {
        Employee caller = getCaller();
        Employee target = getOrThrow(id);

        if (!isAdmin(caller) && !caller.getId().equals(target.getId())) {
            throw new AccessForbiddenException("You can only update your own profile");
        }

        target.setName(request.getName());
        target.setPhoneNumber(request.getPhoneNumber());
        target.setHomeAddress(request.getHomeAddress());
        target.setHomeLatitude(request.getHomeLatitude());
        target.setHomeLongitude(request.getHomeLongitude());
        return toResponse(employeeRepository.save(target));
    }

    @Transactional
    public void delete(Long id) {
        if (!employeeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Employee not found with id: " + id);
        }
        employeeRepository.deleteById(id);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Employee getOrThrow(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
    }

    private Employee getCaller() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        return employeeRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated employee not found"));
    }

    private boolean isAdmin(Employee employee) {
        return Role.ROLE_ADMIN.equals(employee.getRole());
    }

    public EmployeeResponse toResponse(Employee employee) {
        return EmployeeResponse.builder()
                .id(employee.getId())
                .email(employee.getEmail())
                .name(employee.getName())
                .role(employee.getRole())
                .gender(employee.getGender())
                .phoneNumber(employee.getPhoneNumber())
                .homeAddress(employee.getHomeAddress())
                .homeLatitude(employee.getHomeLatitude())
                .homeLongitude(employee.getHomeLongitude())
                .createdAt(employee.getCreatedAt())
                .updatedAt(employee.getUpdatedAt())
                .build();
    }
}
