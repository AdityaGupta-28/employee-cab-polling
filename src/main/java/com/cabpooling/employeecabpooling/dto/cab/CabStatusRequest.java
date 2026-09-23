package com.cabpooling.employeecabpooling.dto.cab;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabStatusRequest {

    @NotNull(message = "isActive flag is required")
    private Boolean isActive;
}
