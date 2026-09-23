package com.cabpooling.employeecabpooling.dto.assignment;

import com.cabpooling.employeecabpooling.model.enums.AssignmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabAssignmentResponse {

    private Long id;
    private Long cabId;
    private String cabLicensePlate;
    private Long shiftId;
    private String shiftName;
    private LocalDate assignmentDate;
    private AssignmentStatus status;
    private Double totalDistanceKm;
    private Integer totalDurationMinutes;
    private Boolean hasEscort;
    private List<PickupStopResponse> stops;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
