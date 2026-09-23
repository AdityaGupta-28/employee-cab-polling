package com.cabpooling.employeecabpooling.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestSecurityController {

    @GetMapping("/public")
    public ResponseEntity<Map<String, String>> publicEndpoint() {
        return ResponseEntity.ok(Map.of("message", "This endpoint is publicly accessible"));
    }

    @GetMapping("/employee-access")
    public ResponseEntity<Map<String, String>> employeeEndpoint(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(Map.of(
                "message", "Welcome, " + userDetails.getUsername(),
                "role", userDetails.getAuthorities().iterator().next().getAuthority()));
    }

    @GetMapping("/admin-only")
    public ResponseEntity<Map<String, String>> adminEndpoint(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(Map.of(
                "message", "Admin panel accessed by " + userDetails.getUsername()));
    }
}
