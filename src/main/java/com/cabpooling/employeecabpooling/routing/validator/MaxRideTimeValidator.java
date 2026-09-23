package com.cabpooling.employeecabpooling.routing.validator;

import com.cabpooling.employeecabpooling.exception.BusinessRuleException;
import com.cabpooling.employeecabpooling.model.entity.CabAssignment;
import com.cabpooling.employeecabpooling.model.entity.PickupStop;
import com.cabpooling.employeecabpooling.model.enums.ShiftType;
import com.cabpooling.employeecabpooling.model.enums.StopType;
import com.cabpooling.employeecabpooling.routing.optimizer.TwoOptRouteOptimizer.OptimizedRouteResult;
import com.cabpooling.employeecabpooling.routing.optimizer.TwoOptRouteOptimizer.OptimizedStop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hard Constraint Validator: Max-Ride-Time Rule (90 minutes maximum per rider).
 * Rejects any pickup/dropoff ordering where an employee spends more than the allowed maximum
 * travel duration inside the vehicle.
 */
@Slf4j
@Component
public class MaxRideTimeValidator {

    public static final int DEFAULT_MAX_RIDE_TIME_MINUTES = 90;

    /**
     * Validates that no employee in the cab assignment exceeds the max ride time limit.
     */
    public void validateAssignment(CabAssignment assignment, int maxRideTimeMinutes) {
        if (assignment == null || assignment.getStops() == null || assignment.getStops().isEmpty()) {
            return;
        }

        List<PickupStop> stops = assignment.getStops();
        ShiftType shiftType = assignment.getShift() != null ? assignment.getShift().getShiftType() : ShiftType.INBOUND;
        int n = stops.size();

        if (shiftType == ShiftType.INBOUND) {
            // Stops: Pickups ..., Office at end (n-1)
            for (int i = 0; i < n - 1; i++) {
                PickupStop stop = stops.get(i);
                if (stop.getStopType() == StopType.PICKUP && stop.getBooking() != null) {
                    int rideTime = 0;
                    for (int j = i + 1; j < n; j++) {
                        rideTime += stops.get(j).getDurationFromPreviousMinutes();
                    }
                    if (rideTime > maxRideTimeMinutes) {
                        String empName = stop.getBooking().getEmployee() != null ? stop.getBooking().getEmployee().getName() : "ID#" + stop.getBooking().getId();
                        throw new BusinessRuleException(String.format(
                                "Max-Ride-Time limit violated: Employee %s ride duration of %d mins exceeds maximum allowed threshold of %d mins",
                                empName, rideTime, maxRideTimeMinutes));
                    }
                }
            }
        } else {
            // OUTBOUND: Office at index 0, Dropoffs ...
            int cumulativeRideTime = 0;
            for (int i = 1; i < n; i++) {
                PickupStop stop = stops.get(i);
                cumulativeRideTime += stop.getDurationFromPreviousMinutes();
                if (stop.getStopType() == StopType.DROPOFF && stop.getBooking() != null) {
                    if (cumulativeRideTime > maxRideTimeMinutes) {
                        String empName = stop.getBooking().getEmployee() != null ? stop.getBooking().getEmployee().getName() : "ID#" + stop.getBooking().getId();
                        throw new BusinessRuleException(String.format(
                                "Max-Ride-Time limit violated: Employee %s ride duration of %d mins exceeds maximum allowed threshold of %d mins",
                                empName, cumulativeRideTime, maxRideTimeMinutes));
                    }
                }
            }
        }
    }

    public void validateAssignment(CabAssignment assignment) {
        validateAssignment(assignment, DEFAULT_MAX_RIDE_TIME_MINUTES);
    }

    /**
     * Validates an OptimizedRouteResult prior to assignment saving.
     */
    public void validateRoute(OptimizedRouteResult routeResult, ShiftType shiftType, int maxRideTimeMinutes) {
        if (routeResult == null || routeResult.getStops() == null || routeResult.getStops().isEmpty()) {
            return;
        }

        List<OptimizedStop> stops = routeResult.getStops();
        int n = stops.size();

        if (shiftType == ShiftType.INBOUND) {
            for (int i = 0; i < n - 1; i++) {
                OptimizedStop stop = stops.get(i);
                if (stop.getStopType() == StopType.PICKUP) {
                    int rideTime = 0;
                    for (int j = i + 1; j < n; j++) {
                        rideTime += stops.get(j).getDurationFromPreviousMinutes();
                    }
                    if (rideTime > maxRideTimeMinutes) {
                        String empInfo = stop.getBooking() != null && stop.getBooking().getEmployee() != null
                                ? stop.getBooking().getEmployee().getName() : stop.getAddress();
                        throw new BusinessRuleException(String.format(
                                "Max-Ride-Time limit violated: Employee %s ride duration of %d mins exceeds maximum allowed threshold of %d mins",
                                empInfo, rideTime, maxRideTimeMinutes));
                    }
                }
            }
        } else {
            int cumulativeRideTime = 0;
            for (int i = 1; i < n; i++) {
                OptimizedStop stop = stops.get(i);
                cumulativeRideTime += stop.getDurationFromPreviousMinutes();
                if (stop.getStopType() == StopType.DROPOFF) {
                    if (cumulativeRideTime > maxRideTimeMinutes) {
                        String empInfo = stop.getBooking() != null && stop.getBooking().getEmployee() != null
                                ? stop.getBooking().getEmployee().getName() : stop.getAddress();
                        throw new BusinessRuleException(String.format(
                                "Max-Ride-Time limit violated: Employee %s ride duration of %d mins exceeds maximum allowed threshold of %d mins",
                                empInfo, cumulativeRideTime, maxRideTimeMinutes));
                    }
                }
            }
        }
    }

    public void validateRoute(OptimizedRouteResult routeResult, ShiftType shiftType) {
        validateRoute(routeResult, shiftType, DEFAULT_MAX_RIDE_TIME_MINUTES);
    }
}
