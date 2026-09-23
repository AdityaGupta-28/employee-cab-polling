package com.cabpooling.employeecabpooling.dto.shift;

import com.cabpooling.employeecabpooling.model.enums.ShiftType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftRequest {

    @NotNull(message = "Office ID is required")
    private Long officeId;

    @NotBlank(message = "Shift name is required")
    private String name;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    private LocalTime endTime;

    @NotNull(message = "Shift type is required (INBOUND or OUTBOUND)")
    private ShiftType shiftType;

    @Min(value = 0, message = "Cutoff minutes must be 0 or greater")
    @Builder.Default
    private int cutoffMinutes = 120;
}
