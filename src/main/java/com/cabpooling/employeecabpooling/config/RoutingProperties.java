package com.cabpooling.employeecabpooling.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.routing")
public class RoutingProperties {

    /** Hard max ride time per employee (minutes). */
    private int maxRideMinutes = 90;

    /** Max distance (km) from cluster seed for a rider to join the same cab. */
    private double maxClusterRadiusKm = 8.0;

    /** Max insertion detour (km) when fitting a late booking. */
    private double maxDetourKm = 30.0;

    /** Geohash precision for spatial bucketing (5 ≈ 4.9 km cells). */
    private int geohashPrecision = 5;

    private NightSafety nightSafety = new NightSafety();

    @Data
    public static class NightSafety {
        /** Inclusive night window start (local), e.g. 20:00. */
        private String start = "20:00";
        /** Inclusive night window end (local), e.g. 06:00. */
        private String end = "06:00";
    }
}
