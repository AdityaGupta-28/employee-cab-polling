package com.cabpooling.employeecabpooling.dto.replanning;

import com.cabpooling.employeecabpooling.dto.assignment.CabAssignmentResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LateBookingInsertResponse {
    private Long bookingId;
    private Long cabAssignmentId;
    private Long cabId;
    private String cabLicensePlate;
    private int insertedAtStopOrder;
    private double detourKm;
    private CabAssignmentResponse assignment;
}
