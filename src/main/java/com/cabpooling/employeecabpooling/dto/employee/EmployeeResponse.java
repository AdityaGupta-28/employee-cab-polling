package com.cabpooling.employeecabpooling.dto.employee;

import com.cabpooling.employeecabpooling.model.enums.Gender;
import com.cabpooling.employeecabpooling.model.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeResponse {

    private Long id;
    private String email;
    private String name;
    private Role role;
    private Gender gender;
    private String phoneNumber;
    private String homeAddress;
    private Double homeLatitude;
    private Double homeLongitude;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
