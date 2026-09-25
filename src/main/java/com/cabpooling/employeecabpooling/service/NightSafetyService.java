package com.cabpooling.employeecabpooling.service;

import com.cabpooling.employeecabpooling.config.RoutingProperties;
import com.cabpooling.employeecabpooling.dto.assignment.EscortRequest;
import com.cabpooling.employeecabpooling.exception.BusinessRuleException;
import com.cabpooling.employeecabpooling.model.entity.CabAssignment;
import com.cabpooling.employeecabpooling.model.entity.Employee;
import com.cabpooling.employeecabpooling.model.entity.PickupStop;
import com.cabpooling.employeecabpooling.model.entity.Shift;
import com.cabpooling.employeecabpooling.model.enums.Gender;
import com.cabpooling.employeecabpooling.model.enums.ShiftType;
import com.cabpooling.employeecabpooling.model.enums.StopType;
import com.cabpooling.employeecabpooling.repository.CabAssignmentRepository;
import com.cabpooling.employeecabpooling.repository.EscortAssignmentRepository;
import com.cabpooling.employeecabpooling.routing.DistanceProvider;
import com.cabpooling.employeecabpooling.routing.DistanceResult;
import com.cabpooling.employeecabpooling.routing.validator.MaxRideTimeValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NightSafetyService {

    private final EscortService escortService;
    private final EscortAssignmentRepository escortAssignmentRepository;
    private final CabAssignmentRepository cabAssignmentRepository;
    private final DistanceProvider distanceProvider;
    private final MaxRideTimeValidator maxRideTimeValidator;
    private final RoutingProperties routingProperties;

    public boolean isNightShift(Shift shift) {
        return isTimeInNightWindow(shift.getStartTime()) || isTimeInNightWindow(shift.getEndTime());
    }

    private boolean isTimeInNightWindow(LocalTime time) {
        if (time == null) {
            return false;
        }
        LocalTime nightStart = LocalTime.parse(routingProperties.getNightSafety().getStart());
        LocalTime nightEnd = LocalTime.parse(routingProperties.getNightSafety().getEnd());
        // Night window crosses midnight: time >= start OR time <= end
        return !time.isBefore(nightStart) || !time.isAfter(nightEnd);
    }

    @Transactional
    public boolean evaluateAndProtect(CabAssignment assignment) {
        Shift shift = assignment.getShift();
        if (!isNightShift(shift)) {
            return false;
        }

        List<PickupStop> stops = assignment.getStops();
        if (stops == null || stops.isEmpty()) {
            return false;
        }

        boolean requiresEscort = false;

        if (shift.getShiftType() == ShiftType.INBOUND) {
            PickupStop firstPassengerStop = stops.stream()
                    .filter(s -> s.getStopType() == StopType.PICKUP && s.getBooking() != null)
                    .findFirst()
                    .orElse(null);

            if (firstPassengerStop != null) {
                Employee employee = firstPassengerStop.getBooking().getEmployee();
                if (employee != null && employee.getGender() == Gender.FEMALE) {
                    boolean reordered = tryReorderInboundWithMale(assignment, stops);
                    if (!reordered) {
                        requiresEscort = true;
                    }
                }
            }
        } else {
            List<PickupStop> passengerStops = stops.stream()
                    .filter(s -> s.getStopType() == StopType.DROPOFF && s.getBooking() != null)
                    .toList();

            if (!passengerStops.isEmpty()) {
                PickupStop lastPassengerStop = passengerStops.get(passengerStops.size() - 1);
                Employee employee = lastPassengerStop.getBooking().getEmployee();
                if (employee != null && employee.getGender() == Gender.FEMALE) {
                    boolean reordered = tryReorderOutboundWithMale(assignment, stops);
                    if (!reordered) {
                        requiresEscort = true;
                    }
                }
            }
        }

        if (requiresEscort) {
            provisionEscort(assignment);
            return true;
        }

        return false;
    }

    private boolean tryReorderInboundWithMale(CabAssignment assignment, List<PickupStop> stops) {
        int maleIndex = -1;
        for (int i = 0; i < stops.size(); i++) {
            PickupStop s = stops.get(i);
            if (s.getStopType() == StopType.PICKUP && s.getBooking() != null
                    && s.getBooking().getEmployee() != null
                    && s.getBooking().getEmployee().getGender() == Gender.MALE) {
                maleIndex = i;
                break;
            }
        }

        if (maleIndex <= 0) {
            return false;
        }

        List<PickupStopSnapshot> snapshot = snapshotStops(stops);
        PickupStop maleStop = stops.remove(maleIndex);
        stops.add(0, maleStop);
        reindexStops(stops);
        recomputeLegMetrics(assignment);

        try {
            maxRideTimeValidator.validateAssignment(assignment, routingProperties.getMaxRideMinutes());
            log.info("Reordered inbound night route for cab assignment id={} to prioritize male pickup",
                    assignment.getId());
            return true;
        } catch (BusinessRuleException ex) {
            restoreStops(stops, snapshot);
            recomputeLegMetrics(assignment);
            log.info("Night reorder rejected by max-ride rule for assignment id={}; escort will be used",
                    assignment.getId());
            return false;
        }
    }

    private boolean tryReorderOutboundWithMale(CabAssignment assignment, List<PickupStop> stops) {
        int maleIndex = -1;
        for (int i = 0; i < stops.size(); i++) {
            PickupStop s = stops.get(i);
            if (s.getStopType() == StopType.DROPOFF && s.getBooking() != null
                    && s.getBooking().getEmployee() != null
                    && s.getBooking().getEmployee().getGender() == Gender.MALE) {
                maleIndex = i;
                break;
            }
        }

        if (maleIndex < 0 || maleIndex >= stops.size() - 1) {
            return false;
        }

        List<PickupStopSnapshot> snapshot = snapshotStops(stops);
        PickupStop maleStop = stops.remove(maleIndex);
        stops.add(maleStop);
        reindexStops(stops);
        recomputeLegMetrics(assignment);

        try {
            maxRideTimeValidator.validateAssignment(assignment, routingProperties.getMaxRideMinutes());
            log.info("Reordered outbound night route for cab assignment id={} to prioritize male last dropoff",
                    assignment.getId());
            return true;
        } catch (BusinessRuleException ex) {
            restoreStops(stops, snapshot);
            recomputeLegMetrics(assignment);
            log.info("Night reorder rejected by max-ride rule for assignment id={}; escort will be used",
                    assignment.getId());
            return false;
        }
    }

    private void reindexStops(List<PickupStop> stops) {
        for (int i = 0; i < stops.size(); i++) {
            stops.get(i).setStopOrder(i + 1);
        }
    }

    /**
     * After a safety reorder, recalculate distances, durations, ETAs and assignment totals
     * so pickup ETAs stay consistent with the new stop order.
     */
    private void recomputeLegMetrics(CabAssignment assignment) {
        List<PickupStop> stops = assignment.getStops();
        Shift shift = assignment.getShift();
        LocalDate date = assignment.getAssignmentDate();
        OffsetDateTime now = OffsetDateTime.now();

        double totalDist = 0.0;
        int totalDur = 0;

        for (int i = 0; i < stops.size(); i++) {
            PickupStop curr = stops.get(i);
            if (i == 0) {
                curr.setDistanceFromPreviousKm(0.0);
                curr.setDurationFromPreviousMinutes(0);
            } else {
                PickupStop prev = stops.get(i - 1);
                DistanceResult r = distanceProvider.calculateDistanceAndDuration(
                        prev.getLatitude(), prev.getLongitude(),
                        curr.getLatitude(), curr.getLongitude());
                curr.setDistanceFromPreviousKm(r.getDistanceKm());
                curr.setDurationFromPreviousMinutes(r.getDurationMinutes());
                totalDist += r.getDistanceKm();
                totalDur += r.getDurationMinutes();
            }
        }

        OffsetDateTime[] plannedTimes = new OffsetDateTime[stops.size()];
        if (shift.getShiftType() == ShiftType.INBOUND) {
            OffsetDateTime officeArrival = date.atTime(shift.getStartTime()).atOffset(now.getOffset());
            OffsetDateTime current = officeArrival.minusMinutes(totalDur);
            for (int i = 0; i < stops.size(); i++) {
                if (i > 0) {
                    current = current.plusMinutes(stops.get(i).getDurationFromPreviousMinutes());
                }
                plannedTimes[i] = current;
            }
            if (!stops.isEmpty()) {
                plannedTimes[stops.size() - 1] = officeArrival;
            }
        } else {
            OffsetDateTime officeDeparture = date.atTime(shift.getEndTime()).atOffset(now.getOffset());
            OffsetDateTime current = officeDeparture;
            for (int i = 0; i < stops.size(); i++) {
                if (i > 0) {
                    current = current.plusMinutes(stops.get(i).getDurationFromPreviousMinutes());
                }
                plannedTimes[i] = current;
            }
        }

        for (int i = 0; i < stops.size(); i++) {
            stops.get(i).setPlannedTime(plannedTimes[i]);
        }

        assignment.setTotalDistanceKm(Math.round(totalDist * 100.0) / 100.0);
        assignment.setTotalDurationMinutes(totalDur);
    }

    private List<PickupStopSnapshot> snapshotStops(List<PickupStop> stops) {
        List<PickupStopSnapshot> snap = new ArrayList<>(stops.size());
        for (PickupStop s : stops) {
            snap.add(new PickupStopSnapshot(
                    s.getBooking(), s.getStopType(), s.getAddress(),
                    s.getLatitude(), s.getLongitude(),
                    s.getDistanceFromPreviousKm(), s.getDurationFromPreviousMinutes(),
                    s.getPlannedTime()));
        }
        return snap;
    }

    private void restoreStops(List<PickupStop> stops, List<PickupStopSnapshot> snapshot) {
        for (int i = 0; i < stops.size() && i < snapshot.size(); i++) {
            PickupStopSnapshot snap = snapshot.get(i);
            PickupStop s = stops.get(i);
            s.setBooking(snap.booking());
            s.setStopType(snap.stopType());
            s.setAddress(snap.address());
            s.setLatitude(snap.lat());
            s.setLongitude(snap.lon());
            s.setDistanceFromPreviousKm(snap.distanceKm());
            s.setDurationFromPreviousMinutes(snap.durationMinutes());
            s.setPlannedTime(snap.plannedTime());
            s.setStopOrder(i + 1);
        }
    }

    private void provisionEscort(CabAssignment assignment) {
        if (!escortAssignmentRepository.existsByCabAssignmentId(assignment.getId())) {
            escortService.create(EscortRequest.builder()
                    .cabAssignmentId(assignment.getId())
                    .escortName("Night Safety Escort (Automated)")
                    .escortContact("+919000000000")
                    .build());
        }
        assignment.setHasEscort(true);
        cabAssignmentRepository.save(assignment);
        log.info("Night safety rule triggered: Automatically assigned escort to cab assignment id={}",
                assignment.getId());
    }

    private record PickupStopSnapshot(
            com.cabpooling.employeecabpooling.model.entity.Booking booking,
            StopType stopType,
            String address,
            double lat,
            double lon,
            double distanceKm,
            int durationMinutes,
            OffsetDateTime plannedTime
    ) {}
}
