package com.cabpooling.employeecabpooling.dto.office;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfficeResponse {

    private Long id;
    private String name;
    private String address;
    private Double latitude;
    private Double longitude;
    private OffsetDateTime createdAt;
}
