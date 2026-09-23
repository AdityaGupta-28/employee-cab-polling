package com.cabpooling.employeecabpooling.routing.optimizer;

import com.cabpooling.employeecabpooling.model.entity.Booking;
import com.cabpooling.employeecabpooling.model.entity.Office;
import com.cabpooling.employeecabpooling.model.enums.ShiftType;
import com.cabpooling.employeecabpooling.model.enums.StopType;
import com.cabpooling.employeecabpooling.routing.DistanceProvider;
import com.cabpooling.employeecabpooling.routing.DistanceResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class TwoOptRouteOptimizer {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptimizedStop {
        private StopType stopType;
        private Booking booking;
        private String address;
        private double latitude;
        private double longitude;
        private double distanceFromPreviousKm;
        private int durationFromPreviousMinutes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptimizedRouteResult {
        private List<OptimizedStop> stops;
        private double totalDistanceKm;
        private int totalDurationMinutes;
    }

    public OptimizedRouteResult optimizeRoute(
            Office office,
            List<Booking> bookings,
            ShiftType shiftType,
            DistanceProvider distanceProvider) {

        if (bookings == null || bookings.isEmpty()) {
            return OptimizedRouteResult.builder()
                    .stops(List.of())
                    .totalDistanceKm(0.0)
                    .totalDurationMinutes(0)
                    .build();
        }

        // 1. Initial Tour using Nearest-Neighbour
        List<OptimizedStop> initialStops = buildInitialTour(office, bookings, shiftType, distanceProvider);

        // 2. Run 2-Opt heuristic optimization
        List<OptimizedStop> optimizedStops = runTwoOpt(initialStops, shiftType, distanceProvider);

        // 3. Compute final leg metrics and totals
        return computeFinalMetrics(optimizedStops, distanceProvider);
    }

    private List<OptimizedStop> buildInitialTour(
            Office office,
            List<Booking> bookings,
            ShiftType shiftType,
            DistanceProvider distanceProvider) {

        List<OptimizedStop> stops = new ArrayList<>();
        List<Booking> unvisited = new ArrayList<>(bookings);

        if (shiftType == ShiftType.INBOUND) {
            // Nearest neighbour from office outward, reversed so office is at end
            double currLat = office.getLatitude();
            double currLon = office.getLongitude();
            List<Booking> ordered = new ArrayList<>();

            while (!unvisited.isEmpty()) {
                Booking closest = null;
                double minDist = Double.MAX_VALUE;
                for (Booking b : unvisited) {
                    double d = distanceProvider.calculateDistanceKm(currLat, currLon, b.getPickupLatitude(), b.getPickupLongitude());
                    if (d < minDist) {
                        minDist = d;
                        closest = b;
                    }
                }
                ordered.add(closest);
                unvisited.remove(closest);
                currLat = closest.getPickupLatitude();
                currLon = closest.getPickupLongitude();
            }

            Collections.reverse(ordered);

            for (Booking b : ordered) {
                stops.add(OptimizedStop.builder()
                        .stopType(StopType.PICKUP)
                        .booking(b)
                        .address(b.getPickupAddress())
                        .latitude(b.getPickupLatitude())
                        .longitude(b.getPickupLongitude())
                        .build());
            }

            stops.add(OptimizedStop.builder()
                    .stopType(StopType.OFFICE)
                    .booking(null)
                    .address(office.getAddress())
                    .latitude(office.getLatitude())
                    .longitude(office.getLongitude())
                    .build());

        } else {
            // OUTBOUND: Office is first, then dropoffs
            stops.add(OptimizedStop.builder()
                    .stopType(StopType.OFFICE)
                    .booking(null)
                    .address(office.getAddress())
                    .latitude(office.getLatitude())
                    .longitude(office.getLongitude())
                    .build());

            double currLat = office.getLatitude();
            double currLon = office.getLongitude();

            while (!unvisited.isEmpty()) {
                Booking closest = null;
                double minDist = Double.MAX_VALUE;
                for (Booking b : unvisited) {
                    double d = distanceProvider.calculateDistanceKm(currLat, currLon, b.getPickupLatitude(), b.getPickupLongitude());
                    if (d < minDist) {
                        minDist = d;
                        closest = b;
                    }
                }
                stops.add(OptimizedStop.builder()
                        .stopType(StopType.DROPOFF)
                        .booking(closest)
                        .address(closest.getPickupAddress())
                        .latitude(closest.getPickupLatitude())
                        .longitude(closest.getPickupLongitude())
                        .build());
                unvisited.remove(closest);
                currLat = closest.getPickupLatitude();
                currLon = closest.getPickupLongitude();
            }
        }

        return stops;
    }

    private List<OptimizedStop> runTwoOpt(
            List<OptimizedStop> stops,
            ShiftType shiftType,
            DistanceProvider distanceProvider) {

        int n = stops.size();
        if (n <= 3) {
            return stops;
        }

        List<OptimizedStop> tour = new ArrayList<>(stops);
        boolean improved = true;
        int maxIterations = 50;
        int iteration = 0;

        // Determine movable index bounds (Office is fixed at index 0 for OUTBOUND, index n-1 for INBOUND)
        int minIdx = (shiftType == ShiftType.OUTBOUND) ? 1 : 0;
        int maxIdx = (shiftType == ShiftType.INBOUND) ? n - 2 : n - 1;

        while (improved && iteration < maxIterations) {
            improved = false;
            iteration++;

            for (int i = minIdx; i < maxIdx; i++) {
                for (int j = i + 1; j <= maxIdx; j++) {
                    double delta = computeTwoOptGain(tour, i, j, distanceProvider);
                    if (delta < -0.01) {
                        // Reverse sub-list from i to j
                        reverseSubList(tour, i, j);
                        improved = true;
                        break;
                    }
                }
                if (improved) {
                    break;
                }
            }
        }

        return tour;
    }

    private double computeTwoOptGain(
            List<OptimizedStop> tour,
            int i,
            int j,
            DistanceProvider distanceProvider) {

        int n = tour.size();
        OptimizedStop prevI = (i > 0) ? tour.get(i - 1) : null;
        OptimizedStop currI = tour.get(i);
        OptimizedStop currJ = tour.get(j);
        OptimizedStop nextJ = (j < n - 1) ? tour.get(j + 1) : null;

        double currentDist = 0.0;
        double newDist = 0.0;

        if (prevI != null) {
            currentDist += distanceProvider.calculateDistanceKm(prevI.getLatitude(), prevI.getLongitude(), currI.getLatitude(), currI.getLongitude());
            newDist += distanceProvider.calculateDistanceKm(prevI.getLatitude(), prevI.getLongitude(), currJ.getLatitude(), currJ.getLongitude());
        }

        if (nextJ != null) {
            currentDist += distanceProvider.calculateDistanceKm(currJ.getLatitude(), currJ.getLongitude(), nextJ.getLatitude(), nextJ.getLongitude());
            newDist += distanceProvider.calculateDistanceKm(currI.getLatitude(), currI.getLongitude(), nextJ.getLatitude(), nextJ.getLongitude());
        }

        return newDist - currentDist;
    }

    private void reverseSubList(List<OptimizedStop> list, int fromIdx, int toIdx) {
        int left = fromIdx;
        int right = toIdx;
        while (left < right) {
            OptimizedStop temp = list.get(left);
            list.set(left, list.get(right));
            list.set(right, temp);
            left++;
            right--;
        }
    }

    private OptimizedRouteResult computeFinalMetrics(
            List<OptimizedStop> stops,
            DistanceProvider distanceProvider) {

        double totalDistance = 0.0;
        int totalDuration = 0;

        for (int i = 0; i < stops.size(); i++) {
            if (i == 0) {
                stops.get(i).setDistanceFromPreviousKm(0.0);
                stops.get(i).setDurationFromPreviousMinutes(0);
            } else {
                OptimizedStop prev = stops.get(i - 1);
                OptimizedStop curr = stops.get(i);
                DistanceResult result = distanceProvider.calculateDistanceAndDuration(
                        prev.getLatitude(), prev.getLongitude(),
                        curr.getLatitude(), curr.getLongitude());

                curr.setDistanceFromPreviousKm(result.getDistanceKm());
                curr.setDurationFromPreviousMinutes(result.getDurationMinutes());
                totalDistance += result.getDistanceKm();
                totalDuration += result.getDurationMinutes();
            }
        }

        return OptimizedRouteResult.builder()
                .stops(stops)
                .totalDistanceKm(Math.round(totalDistance * 100.0) / 100.0)
                .totalDurationMinutes(totalDuration)
                .build();
    }
}
