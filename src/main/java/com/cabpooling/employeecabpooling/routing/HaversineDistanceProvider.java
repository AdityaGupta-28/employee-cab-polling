package com.cabpooling.employeecabpooling.routing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("haversineDistanceProvider")
@RequiredArgsConstructor
public class HaversineDistanceProvider implements DistanceProvider {

    private final DistanceCacheService cacheService;
    private static final double AVG_SPEED_KMH = 30.0;
    private static final int EARTH_RADIUS_KM = 6371;

    @Override
    public DistanceResult calculateDistanceAndDuration(double lat1, double lon1, double lat2, double lon2) {
        DistanceResult cached = cacheService.get(lat1, lon1, lat2, lon2);
        if (cached != null) {
            return cached;
        }

        double distance = calculateDistanceKm(lat1, lon1, lat2, lon2);
        int duration = calculateDurationMinutes(lat1, lon1, lat2, lon2);

        DistanceResult result = DistanceResult.builder()
                .distanceKm(distance)
                .durationMinutes(duration)
                .build();

        cacheService.put(lat1, lon1, lat2, lon2, result);
        return result;
    }

    @Override
    public double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(EARTH_RADIUS_KM * c * 100.0) / 100.0;
    }

    @Override
    public int calculateDurationMinutes(double lat1, double lon1, double lat2, double lon2) {
        double distance = calculateDistanceKm(lat1, lon1, lat2, lon2);
        int duration = (int) Math.round((distance / AVG_SPEED_KMH) * 60.0);
        if (distance > 0.05 && duration == 0) {
            duration = 1;
        }
        return duration;
    }

    @Override
    public String getProviderName() {
        return "HAVERSINE";
    }
}
