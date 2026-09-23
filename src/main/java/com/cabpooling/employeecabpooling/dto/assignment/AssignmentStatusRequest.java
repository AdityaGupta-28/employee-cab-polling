package com.cabpooling.employeecabpooling.dto.assignment;

import com.cabpooling.employeecabpooling.model.enums.AssignmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentStatusRequest {

    @NotNull(message = "Status is required")
    private AssignmentStatus status;
}
