package com.cabpooling.employeecabpooling.dto.allocation;

import com.cabpooling.employeecabpooling.dto.assignment.CabAssignmentResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoClusterResponse {

    private Long shiftId;
    private String shiftName;
    private LocalDate assignmentDate;
    private Integer totalBookingsClustered;
    private Integer totalCabsAssigned;
    private List<CabAssignmentResponse> assignments;
}
