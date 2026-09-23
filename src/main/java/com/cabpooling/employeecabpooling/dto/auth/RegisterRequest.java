package com.cabpooling.employeecabpooling.dto.auth;

import com.cabpooling.employeecabpooling.model.enums.Gender;
import com.cabpooling.employeecabpooling.model.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Valid email is required")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Name is required")
    private String name;

    private Role role; // Defaults to ROLE_EMPLOYEE if null

    @NotNull(message = "Gender is required")
    private Gender gender;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotBlank(message = "Home address is required")
    private String homeAddress;

    @NotNull(message = "Home latitude is required")
    private Double homeLatitude;

    @NotNull(message = "Home longitude is required")
    private Double homeLongitude;
}
