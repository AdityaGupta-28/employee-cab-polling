package com.cabpooling.employeecabpooling.routing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistanceResult {
    private double distanceKm;
    private int durationMinutes;
}
