package com.cabpooling.employeecabpooling.service;

import com.cabpooling.employeecabpooling.dto.assignment.EscortRequest;
import com.cabpooling.employeecabpooling.model.entity.CabAssignment;
import com.cabpooling.employeecabpooling.model.entity.Employee;
import com.cabpooling.employeecabpooling.model.entity.PickupStop;
import com.cabpooling.employeecabpooling.model.entity.Shift;
import com.cabpooling.employeecabpooling.model.enums.Gender;
import com.cabpooling.employeecabpooling.model.enums.ShiftType;
import com.cabpooling.employeecabpooling.model.enums.StopType;
import com.cabpooling.employeecabpooling.repository.CabAssignmentRepository;
import com.cabpooling.employeecabpooling.repository.EscortAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NightSafetyService {

    private final EscortService escortService;
    private final EscortAssignmentRepository escortAssignmentRepository;
    private final CabAssignmentRepository cabAssignmentRepository;

    private static final LocalTime NIGHT_START = LocalTime.of(20, 0); // 8:00 PM
    private static final LocalTime NIGHT_END = LocalTime.of(6, 0);    // 6:00 AM

    public boolean isNightShift(Shift shift) {
        return isTimeInNightWindow(shift.getStartTime()) || isTimeInNightWindow(shift.getEndTime());
    }

    private boolean isTimeInNightWindow(LocalTime time) {
        if (time == null) return false;
        // Night window crosses midnight: time >= 20:00 OR time <= 06:00
        return !time.isBefore(NIGHT_START) || !time.isAfter(NIGHT_END);
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
            // Find first passenger stop (PICKUP)
            PickupStop firstPassengerStop = stops.stream()
                    .filter(s -> s.getStopType() == StopType.PICKUP && s.getBooking() != null)
                    .findFirst()
                    .orElse(null);

            if (firstPassengerStop != null) {
                Employee employee = firstPassengerStop.getBooking().getEmployee();
                if (employee != null && employee.getGender() == Gender.FEMALE) {
                    // Female is first pickup. Check if reordering is possible.
                    boolean reordered = tryReorderInboundWithMale(assignment, stops);
                    if (!reordered) {
                        requiresEscort = true;
                    }
                }
            }
        } else {
            // OUTBOUND: Find last passenger stop (DROPOFF)
            List<PickupStop> passengerStops = stops.stream()
                    .filter(s -> s.getStopType() == StopType.DROPOFF && s.getBooking() != null)
                    .toList();

            if (!passengerStops.isEmpty()) {
                PickupStop lastPassengerStop = passengerStops.get(passengerStops.size() - 1);
                Employee employee = lastPassengerStop.getBooking().getEmployee();
                if (employee != null && employee.getGender() == Gender.FEMALE) {
                    // Female is last dropoff. Check if reordering is possible.
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
        // Find index of first male passenger stop
        int maleIndex = -1;
        for (int i = 0; i < stops.size(); i++) {
            PickupStop s = stops.get(i);
            if (s.getStopType() == StopType.PICKUP && s.getBooking() != null && s.getBooking().getEmployee() != null) {
                if (s.getBooking().getEmployee().getGender() == Gender.MALE) {
                    maleIndex = i;
                    break;
                }
            }
        }

        if (maleIndex > 0) {
            // Swap male passenger to first pickup position
            PickupStop maleStop = stops.remove(maleIndex);
            stops.add(0, maleStop);
            reindexStops(stops);
            log.info("Reordered inbound night route for cab assignment id={} to prioritize male pickup", assignment.getId());
            return true;
        }
        return false;
    }

    private boolean tryReorderOutboundWithMale(CabAssignment assignment, List<PickupStop> stops) {
        // Find a male passenger and move them to be the last dropoff (before office or at end)
        int maleIndex = -1;
        for (int i = 0; i < stops.size(); i++) {
            PickupStop s = stops.get(i);
            if (s.getStopType() == StopType.DROPOFF && s.getBooking() != null && s.getBooking().getEmployee() != null) {
                if (s.getBooking().getEmployee().getGender() == Gender.MALE) {
                    maleIndex = i;
                    break;
                }
            }
        }

        if (maleIndex >= 0 && maleIndex < stops.size() - 1) {
            // Move male stop to last dropoff position
            PickupStop maleStop = stops.remove(maleIndex);
            stops.add(maleStop);
            reindexStops(stops);
            log.info("Reordered outbound night route for cab assignment id={} to prioritize male last dropoff", assignment.getId());
            return true;
        }
        return false;
    }

    private void reindexStops(List<PickupStop> stops) {
        for (int i = 0; i < stops.size(); i++) {
            stops.get(i).setStopOrder(i + 1);
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
        log.info("Night safety rule triggered: Automatically assigned escort to cab assignment id={}", assignment.getId());
    }
}
