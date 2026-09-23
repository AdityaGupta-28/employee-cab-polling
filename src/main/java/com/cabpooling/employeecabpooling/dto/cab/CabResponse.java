package com.cabpooling.employeecabpooling.dto.cab;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabResponse {

    private Long id;
    private String licensePlate;
    private String model;
    private Integer capacity;
    private String driverName;
    private String driverPhone;
    private Boolean isActive;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
