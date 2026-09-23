package com.cabpooling.employeecabpooling.dto.assignment;

import com.cabpooling.employeecabpooling.model.enums.EscortStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscortStatusRequest {

    @NotNull(message = "Status is required")
    private EscortStatus status;
}
