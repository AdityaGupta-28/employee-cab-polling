package com.cabpooling.employeecabpooling.dto.auth;

import com.cabpooling.employeecabpooling.model.enums.Gender;
import com.cabpooling.employeecabpooling.model.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    @Builder.Default
    private String tokenType = "Bearer";
    private Long id;
    private String email;
    private String name;
    private Role role;
    private Gender gender;
}
