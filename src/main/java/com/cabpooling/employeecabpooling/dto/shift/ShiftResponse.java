package com.cabpooling.employeecabpooling.dto.shift;

import com.cabpooling.employeecabpooling.model.enums.ShiftType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftResponse {

    private Long id;
    private Long officeId;
    private String officeName;
    private String name;
    private LocalTime startTime;
    private LocalTime endTime;
    private ShiftType shiftType;
    private Integer cutoffMinutes;
    private OffsetDateTime createdAt;
}
