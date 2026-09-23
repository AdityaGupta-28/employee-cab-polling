package com.cabpooling.employeecabpooling.dto.allocation;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoClusterRequest {

    @NotNull(message = "Shift ID is required")
    private Long shiftId;

    @NotNull(message = "Assignment date is required")
    @FutureOrPresent(message = "Assignment date cannot be in the past")
    private LocalDate assignmentDate;
}
