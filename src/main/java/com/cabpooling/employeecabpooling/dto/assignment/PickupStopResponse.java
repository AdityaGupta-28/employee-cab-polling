package com.cabpooling.employeecabpooling.dto.assignment;

import com.cabpooling.employeecabpooling.model.enums.StopType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PickupStopResponse {

    private Long id;
    private Integer stopOrder;
    private StopType stopType;
    private String address;
    private Double latitude;
    private Double longitude;
    private OffsetDateTime plannedTime;
    private OffsetDateTime actualTime;
    private Double distanceFromPreviousKm;
    private Integer durationFromPreviousMinutes;
    private Long bookingId;
    private String employeeName;
}
