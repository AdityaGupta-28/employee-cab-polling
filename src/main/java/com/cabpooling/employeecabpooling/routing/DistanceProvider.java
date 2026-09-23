package com.cabpooling.employeecabpooling.routing;

public interface DistanceProvider {
    DistanceResult calculateDistanceAndDuration(double lat1, double lon1, double lat2, double lon2);
    double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2);
    int calculateDurationMinutes(double lat1, double lon1, double lat2, double lon2);
    String getProviderName();
}
