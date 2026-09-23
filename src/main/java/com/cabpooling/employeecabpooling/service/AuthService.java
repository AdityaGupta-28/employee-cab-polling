package com.cabpooling.employeecabpooling.service;

import com.cabpooling.employeecabpooling.dto.auth.*;
import com.cabpooling.employeecabpooling.model.entity.Employee;
import com.cabpooling.employeecabpooling.model.enums.Role;
import com.cabpooling.employeecabpooling.repository.EmployeeRepository;
import com.cabpooling.employeecabpooling.security.CustomUserDetails;
import com.cabpooling.employeecabpooling.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        if (employeeRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        }

        Role role = (request.getRole() != null) ? request.getRole() : Role.ROLE_EMPLOYEE;

        Employee employee = Employee.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .role(role)
                .gender(request.getGender())
                .phoneNumber(request.getPhoneNumber())
                .homeAddress(request.getHomeAddress())
                .homeLatitude(request.getHomeLatitude())
                .homeLongitude(request.getHomeLongitude())
                .build();

        employee = employeeRepository.save(employee);

        CustomUserDetails userDetails = new CustomUserDetails(employee);
        Map<String, Object> claims = buildClaims(employee);
        String token = jwtService.generateToken(userDetails, claims);

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .id(employee.getId())
                .email(employee.getEmail())
                .name(employee.getName())
                .role(employee.getRole())
                .gender(employee.getGender())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        Employee employee = employeeRepository.findByEmail(userDetails.getUsername())
                .orElseThrow();

        Map<String, Object> claims = buildClaims(employee);
        String token = jwtService.generateToken(userDetails, claims);

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .id(employee.getId())
                .email(employee.getEmail())
                .name(employee.getName())
                .role(employee.getRole())
                .gender(employee.getGender())
                .build();
    }

    public UserProfileResponse getCurrentUser(String email) {
        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + email));

        return UserProfileResponse.builder()
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
                .build();
    }

    private Map<String, Object> buildClaims(Employee employee) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", employee.getId());
        claims.put("role", employee.getRole().name());
        claims.put("name", employee.getName());
        return claims;
    }
}
