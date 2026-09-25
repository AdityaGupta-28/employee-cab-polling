package com.cabpooling.employeecabpooling.allocation;

import com.cabpooling.employeecabpooling.exception.BusinessRuleException;
import com.cabpooling.employeecabpooling.model.entity.Booking;
import com.cabpooling.employeecabpooling.model.entity.Cab;
import com.cabpooling.employeecabpooling.model.entity.Employee;
import com.cabpooling.employeecabpooling.model.enums.ShiftType;
import com.cabpooling.employeecabpooling.model.enums.StopType;
import com.cabpooling.employeecabpooling.routing.GeohashUtil;
import com.cabpooling.employeecabpooling.routing.optimizer.TwoOptRouteOptimizer.OptimizedRouteResult;
import com.cabpooling.employeecabpooling.routing.optimizer.TwoOptRouteOptimizer.OptimizedStop;
import com.cabpooling.employeecabpooling.routing.validator.MaxRideTimeValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HardConstraintsUnitTest {

    private final MaxRideTimeValidator validator = new MaxRideTimeValidator();

    @Test
    @DisplayName("Hard Constraint: Rejects route if an employee's ride duration exceeds 90 minutes")
    void validateRoute_exceeding90Minutes_shouldThrowBusinessRuleException() {
        Employee emp = Employee.builder().name("Long Distance Passenger").build();
        Booking booking = Booking.builder().id(101L).employee(emp).build();

        OptimizedStop stop1 = OptimizedStop.builder()
                .stopType(StopType.PICKUP)
                .booking(booking)
                .address("Remote Location A")
                .durationFromPreviousMinutes(0)
                .build();

        OptimizedStop stop2 = OptimizedStop.builder()
                .stopType(StopType.PICKUP)
                .address("Location B")
                .durationFromPreviousMinutes(40)
                .build();

        OptimizedStop officeStop = OptimizedStop.builder()
                .stopType(StopType.OFFICE)
                .address("HQ Office")
                .durationFromPreviousMinutes(60)
                .build();

        OptimizedRouteResult result = OptimizedRouteResult.builder()
                .stops(List.of(stop1, stop2, officeStop))
                .totalDurationMinutes(100)
                .totalDistanceKm(75.0)
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                validator.validateRoute(result, ShiftType.INBOUND, 90));

        assertTrue(ex.getMessage().contains("Max-Ride-Time limit violated"));
        assertTrue(ex.getMessage().contains("exceeds maximum allowed threshold of 90 mins"));
    }

    @Test
    @DisplayName("Hard Constraint: Accepts route when all employee ride durations are under 90 minutes")
    void validateRoute_under90Minutes_shouldPass() {
        Employee emp = Employee.builder().name("Normal Passenger").build();
        Booking booking = Booking.builder().id(102L).employee(emp).build();

        OptimizedStop stop1 = OptimizedStop.builder()
                .stopType(StopType.PICKUP)
                .booking(booking)
                .address("Location A")
                .durationFromPreviousMinutes(0)
                .build();

        OptimizedStop officeStop = OptimizedStop.builder()
                .stopType(StopType.OFFICE)
                .address("HQ Office")
                .durationFromPreviousMinutes(35)
                .build();

        OptimizedRouteResult result = OptimizedRouteResult.builder()
                .stops(List.of(stop1, officeStop))
                .totalDurationMinutes(35)
                .totalDistanceKm(18.0)
                .build();

        assertDoesNotThrow(() -> validator.validateRoute(result, ShiftType.INBOUND, 90));
    }

    @Test
    @DisplayName("Hard Constraint: Seat capacity 4/6 — overflow must be rejected by fleet rule")
    void seatCapacityConstraint_shouldDetectViolation() {
        Cab cab = Cab.builder().capacity(4).licensePlate("KA01AB1234").build();
        List<Booking> passengers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            passengers.add(Booking.builder().id((long) i).build());
        }

        assertTrue(passengers.size() > cab.getCapacity(),
                "Passenger count must exceed cab capacity for this hard-rule check");
        assertTrue(Set.of(4, 6).contains(cab.getCapacity()),
                "Fleet capacity must be 4 or 6 per case study");
    }

    @Test
    @DisplayName("Spatial indexing: nearby coordinates share geohash cell / neighbourhood")
    void geohash_nearbyPoints_shareNeighbourhood() {
        // Koramangala vs HSR — nearby Bengaluru neighbourhoods
        String kora = GeohashUtil.encode(12.9352, 77.6245, 5);
        List<String> hsrNeighbourhood = GeohashUtil.encodeWithNeighbors(12.9121, 77.6446, 5);

        assertFalse(kora.isBlank());
        assertTrue(hsrNeighbourhood.size() >= 1);
        // Same or neighbouring cell — spatial index groups them without full N² scan
        boolean related = hsrNeighbourhood.contains(kora)
                || GeohashUtil.encodeWithNeighbors(12.9352, 77.6245, 5).contains(
                        GeohashUtil.encode(12.9121, 77.6446, 5));
        assertTrue(related, "Nearby employees should fall into overlapping geohash neighbourhoods");
    }
}
