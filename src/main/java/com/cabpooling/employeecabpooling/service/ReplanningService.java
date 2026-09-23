package com.cabpooling.employeecabpooling.service;

import com.cabpooling.employeecabpooling.dto.assignment.CabAssignmentResponse;
import com.cabpooling.employeecabpooling.dto.assignment.PickupStopResponse;
import com.cabpooling.employeecabpooling.dto.replanning.LateBookingInsertResponse;
import com.cabpooling.employeecabpooling.exception.BusinessRuleException;
import com.cabpooling.employeecabpooling.exception.ResourceNotFoundException;
import com.cabpooling.employeecabpooling.model.entity.Booking;
import com.cabpooling.employeecabpooling.model.entity.CabAssignment;
import com.cabpooling.employeecabpooling.model.entity.PickupStop;
import com.cabpooling.employeecabpooling.model.entity.Shift;
import com.cabpooling.employeecabpooling.model.enums.AssignmentStatus;
import com.cabpooling.employeecabpooling.model.enums.BookingStatus;
import com.cabpooling.employeecabpooling.model.enums.ShiftType;
import com.cabpooling.employeecabpooling.model.enums.StopType;
import com.cabpooling.employeecabpooling.repository.BookingRepository;
import com.cabpooling.employeecabpooling.repository.CabAssignmentRepository;
import com.cabpooling.employeecabpooling.repository.EscortAssignmentRepository;
import com.cabpooling.employeecabpooling.repository.PickupStopRepository;
import com.cabpooling.employeecabpooling.routing.DistanceProvider;
import com.cabpooling.employeecabpooling.routing.DistanceResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReplanningService {

    private final BookingRepository bookingRepository;
    private final CabAssignmentRepository cabAssignmentRepository;
    private final PickupStopRepository pickupStopRepository;
    private final EscortAssignmentRepository escortAssignmentRepository;
    private final DistanceProvider distanceProvider;
    private final NightSafetyService nightSafetyService;

    private static final double MAX_DETOUR_KM_THRESHOLD = 30.0;

    @Transactional
    public CabAssignmentResponse handleCancellation(Long bookingId) {
        Optional<PickupStop> stopOpt = pickupStopRepository.findByBookingId(bookingId);
        if (stopOpt.isEmpty()) {
            return null; // Booking was not yet assigned to any cab
        }

        PickupStop stopToRemove = stopOpt.get();
        CabAssignment assignment = stopToRemove.getCabAssignment();

        log.info("Handling cancellation of booking id={} from cab assignment id={}", bookingId, assignment.getId());

        // Build target stop list from remaining stops
        List<StopData> targetStops = new ArrayList<>();
        for (PickupStop s : assignment.getStops()) {
            if (!s.getId().equals(stopToRemove.getId())) {
                targetStops.add(new StopData(s.getStopType(), s.getBooking(), s.getAddress(),
                        s.getLatitude(), s.getLongitude()));
            }
        }

        long passengerCount = targetStops.stream()
                .filter(s -> s.booking != null)
                .count();

        if (passengerCount == 0) {
            // No remaining passengers
            assignment.getStops().clear();
            assignment.setTotalDistanceKm(0.0);
            assignment.setTotalDurationMinutes(0);
            if (Boolean.TRUE.equals(assignment.getHasEscort())) {
                escortAssignmentRepository.findByCabAssignmentId(assignment.getId())
                        .ifPresent(escortAssignmentRepository::delete);
                assignment.setHasEscort(false);
            }
            CabAssignment saved = cabAssignmentRepository.save(assignment);
            return mapToResponse(saved);
        }

        // In-place update route metrics with remaining stops
        applyTargetStops(assignment, targetStops);
        CabAssignment saved = cabAssignmentRepository.save(assignment);

        // Re-evaluate night safety rules (e.g. if sole passenger is now female)
        nightSafetyService.evaluateAndProtect(saved);
        saved = cabAssignmentRepository.save(saved);

        return mapToResponse(saved);
    }

    @Transactional
    public LateBookingInsertResponse insertLateBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleException("Only CONFIRMED bookings can be inserted into a cab");
        }

        if (pickupStopRepository.findByBookingId(bookingId).isPresent()) {
            throw new BusinessRuleException("Booking id " + bookingId + " is already assigned to a cab");
        }

        LocalDate date = booking.getBookingDate();
        Shift shift = booking.getShift();

        List<CabAssignment> candidateAssignments = cabAssignmentRepository
                .findByAssignmentDateAndShiftId(date, shift.getId()).stream()
                .filter(a -> a.getStatus() == AssignmentStatus.PLANNED || a.getStatus() == AssignmentStatus.IN_PROGRESS)
                .toList();

        if (candidateAssignments.isEmpty()) {
            throw new BusinessRuleException("No active cab assignments available for shift on " + date);
        }

        CabAssignment bestAssignment = null;
        int bestInsertIdx = -1;
        double minDetour = Double.MAX_VALUE;

        for (CabAssignment candidate : candidateAssignments) {
            long currentPassengers = candidate.getStops().stream()
                    .filter(s -> s.getBooking() != null)
                    .count();

            if (currentPassengers >= candidate.getCab().getCapacity()) {
                continue; // Cab is full
            }

            List<PickupStop> stops = candidate.getStops();
            if (stops.isEmpty()) {
                // Empty assignment
                bestAssignment = candidate;
                bestInsertIdx = 0;
                minDetour = 0.0;
                break;
            }

            // Find best insertion index
            int minIdx = (shift.getShiftType() == ShiftType.OUTBOUND) ? 1 : 0;
            int maxIdx = (shift.getShiftType() == ShiftType.INBOUND) ? stops.size() - 1 : stops.size();

            for (int k = minIdx; k <= maxIdx; k++) {
                double detour = computeInsertionDetour(stops, k, booking.getPickupLatitude(), booking.getPickupLongitude());
                if (detour < minDetour) {
                    minDetour = detour;
                    bestAssignment = candidate;
                    bestInsertIdx = k;
                }
            }
        }

        if (bestAssignment == null || minDetour > MAX_DETOUR_KM_THRESHOLD) {
            throw new BusinessRuleException("No suitable cab found with capacity and acceptable detour for late booking");
        }

        // Build target stop list in new order
        List<PickupStop> existingStops = bestAssignment.getStops();
        StopType stopType = (shift.getShiftType() == ShiftType.INBOUND) ? StopType.PICKUP : StopType.DROPOFF;

        List<StopData> targetStops = new ArrayList<>();
        for (int i = 0; i < existingStops.size(); i++) {
            if (i == bestInsertIdx) {
                targetStops.add(new StopData(stopType, booking, booking.getPickupAddress(),
                        booking.getPickupLatitude(), booking.getPickupLongitude()));
            }
            PickupStop s = existingStops.get(i);
            targetStops.add(new StopData(s.getStopType(), s.getBooking(), s.getAddress(),
                    s.getLatitude(), s.getLongitude()));
        }
        if (bestInsertIdx == existingStops.size()) {
            targetStops.add(new StopData(stopType, booking, booking.getPickupAddress(),
                    booking.getPickupLatitude(), booking.getPickupLongitude()));
        }

        applyTargetStops(bestAssignment, targetStops);
        bestAssignment = cabAssignmentRepository.save(bestAssignment);

        nightSafetyService.evaluateAndProtect(bestAssignment);
        bestAssignment = cabAssignmentRepository.save(bestAssignment);

        log.info("Inserted late booking id={} into cab assignment id={} at index {} with detour {}km",
                bookingId, bestAssignment.getId(), bestInsertIdx + 1, Math.round(minDetour * 100.0) / 100.0);

        return LateBookingInsertResponse.builder()
                .bookingId(booking.getId())
                .cabAssignmentId(bestAssignment.getId())
                .cabId(bestAssignment.getCab().getId())
                .cabLicensePlate(bestAssignment.getCab().getLicensePlate())
                .insertedAtStopOrder(bestInsertIdx + 1)
                .detourKm(Math.round(minDetour * 100.0) / 100.0)
                .assignment(mapToResponse(bestAssignment))
                .build();
    }

    private double computeInsertionDetour(List<PickupStop> stops, int insertIdx, double newLat, double newLon) {
        if (stops.isEmpty()) {
            return 0.0;
        }

        if (insertIdx == 0) {
            PickupStop next = stops.get(0);
            return distanceProvider.calculateDistanceKm(newLat, newLon, next.getLatitude(), next.getLongitude());
        }

        if (insertIdx == stops.size()) {
            PickupStop prev = stops.get(stops.size() - 1);
            return distanceProvider.calculateDistanceKm(prev.getLatitude(), prev.getLongitude(), newLat, newLon);
        }

        PickupStop prev = stops.get(insertIdx - 1);
        PickupStop next = stops.get(insertIdx);

        double originalLeg = distanceProvider.calculateDistanceKm(
                prev.getLatitude(), prev.getLongitude(), next.getLatitude(), next.getLongitude());
        double newLeg1 = distanceProvider.calculateDistanceKm(
                prev.getLatitude(), prev.getLongitude(), newLat, newLon);
        double newLeg2 = distanceProvider.calculateDistanceKm(
                newLat, newLon, next.getLatitude(), next.getLongitude());

        return (newLeg1 + newLeg2) - originalLeg;
    }

    private void applyTargetStops(CabAssignment assignment, List<StopData> targetStops) {
        List<PickupStop> existingStops = assignment.getStops();
        Shift shift = assignment.getShift();
        LocalDate date = assignment.getAssignmentDate();
        OffsetDateTime now = OffsetDateTime.now();

        double totalDist = 0.0;
        int totalDur = 0;
        double[] legDistances = new double[targetStops.size()];
        int[] legDurations = new int[targetStops.size()];

        for (int i = 0; i < targetStops.size(); i++) {
            if (i == 0) {
                legDistances[i] = 0.0;
                legDurations[i] = 0;
            } else {
                StopData prev = targetStops.get(i - 1);
                StopData curr = targetStops.get(i);
                DistanceResult r = distanceProvider.calculateDistanceAndDuration(
                        prev.lat, prev.lon, curr.lat, curr.lon);
                legDistances[i] = r.getDistanceKm();
                legDurations[i] = r.getDurationMinutes();
                totalDist += r.getDistanceKm();
                totalDur += r.getDurationMinutes();
            }
        }

        // Planned times
        OffsetDateTime[] plannedTimes = new OffsetDateTime[targetStops.size()];
        if (shift.getShiftType() == ShiftType.INBOUND) {
            OffsetDateTime officeArrival = date.atTime(shift.getStartTime()).atOffset(now.getOffset());
            OffsetDateTime current = officeArrival.minusMinutes(totalDur);
            for (int i = 0; i < targetStops.size(); i++) {
                if (i > 0) {
                    current = current.plusMinutes(legDurations[i]);
                }
                plannedTimes[i] = current;
            }
            if (!targetStops.isEmpty()) {
                plannedTimes[targetStops.size() - 1] = officeArrival;
            }
        } else {
            OffsetDateTime officeDeparture = date.atTime(shift.getEndTime()).atOffset(now.getOffset());
            OffsetDateTime current = officeDeparture;
            for (int i = 0; i < targetStops.size(); i++) {
                if (i > 0) {
                    current = current.plusMinutes(legDurations[i]);
                }
                plannedTimes[i] = current;
            }
        }

        // In-place update existing elements or append new ones
        for (int i = 0; i < targetStops.size(); i++) {
            StopData target = targetStops.get(i);
            if (i < existingStops.size()) {
                PickupStop existing = existingStops.get(i);
                existing.setBooking(target.booking);
                existing.setStopOrder(i + 1);
                existing.setStopType(target.type);
                existing.setLatitude(target.lat);
                existing.setLongitude(target.lon);
                existing.setAddress(target.address);
                existing.setDistanceFromPreviousKm(legDistances[i]);
                existing.setDurationFromPreviousMinutes(legDurations[i]);
                existing.setPlannedTime(plannedTimes[i]);
            } else {
                PickupStop newStop = PickupStop.builder()
                        .cabAssignment(assignment)
                        .booking(target.booking)
                        .stopOrder(i + 1)
                        .stopType(target.type)
                        .latitude(target.lat)
                        .longitude(target.lon)
                        .address(target.address)
                        .distanceFromPreviousKm(legDistances[i])
                        .durationFromPreviousMinutes(legDurations[i])
                        .plannedTime(plannedTimes[i])
                        .build();
                existingStops.add(newStop);
            }
        }

        while (existingStops.size() > targetStops.size()) {
            existingStops.remove(existingStops.size() - 1);
        }

        assignment.setTotalDistanceKm(Math.round(totalDist * 100.0) / 100.0);
        assignment.setTotalDurationMinutes(totalDur);
    }

    private record StopData(
            StopType type,
            Booking booking,
            String address,
            double lat,
            double lon
    ) {}

    private CabAssignmentResponse mapToResponse(CabAssignment assignment) {
        List<PickupStopResponse> stopResponses = assignment.getStops() != null
                ? assignment.getStops().stream().map(this::mapStopToResponse).toList()
                : List.of();

        return CabAssignmentResponse.builder()
                .id(assignment.getId())
                .cabId(assignment.getCab() != null ? assignment.getCab().getId() : null)
                .cabLicensePlate(assignment.getCab() != null ? assignment.getCab().getLicensePlate() : null)
                .shiftId(assignment.getShift() != null ? assignment.getShift().getId() : null)
                .shiftName(assignment.getShift() != null ? assignment.getShift().getName() : null)
                .assignmentDate(assignment.getAssignmentDate())
                .status(assignment.getStatus())
                .totalDistanceKm(assignment.getTotalDistanceKm())
                .totalDurationMinutes(assignment.getTotalDurationMinutes())
                .hasEscort(assignment.getHasEscort())
                .stops(stopResponses)
                .createdAt(assignment.getCreatedAt())
                .updatedAt(assignment.getUpdatedAt())
                .build();
    }

    private PickupStopResponse mapStopToResponse(PickupStop stop) {
        return PickupStopResponse.builder()
                .id(stop.getId())
                .stopOrder(stop.getStopOrder())
                .stopType(stop.getStopType())
                .address(stop.getAddress())
                .latitude(stop.getLatitude())
                .longitude(stop.getLongitude())
                .plannedTime(stop.getPlannedTime())
                .actualTime(stop.getActualTime())
                .distanceFromPreviousKm(stop.getDistanceFromPreviousKm())
                .durationFromPreviousMinutes(stop.getDurationFromPreviousMinutes())
                .bookingId(stop.getBooking() != null ? stop.getBooking().getId() : null)
                .employeeName(stop.getBooking() != null && stop.getBooking().getEmployee() != null
                        ? stop.getBooking().getEmployee().getName() : null)
                .build();
    }
}
