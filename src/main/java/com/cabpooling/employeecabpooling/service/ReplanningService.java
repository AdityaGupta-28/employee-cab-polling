package com.cabpooling.employeecabpooling.service;

import com.cabpooling.employeecabpooling.config.RoutingProperties;
import com.cabpooling.employeecabpooling.dto.assignment.CabAssignmentResponse;
import com.cabpooling.employeecabpooling.dto.replanning.LateBookingInsertResponse;
import com.cabpooling.employeecabpooling.exception.BusinessRuleException;
import com.cabpooling.employeecabpooling.exception.ResourceNotFoundException;
import com.cabpooling.employeecabpooling.model.entity.Booking;
import com.cabpooling.employeecabpooling.model.entity.CabAssignment;
import com.cabpooling.employeecabpooling.model.entity.Office;
import com.cabpooling.employeecabpooling.model.entity.PickupStop;
import com.cabpooling.employeecabpooling.model.entity.Shift;
import com.cabpooling.employeecabpooling.model.enums.AssignmentStatus;
import com.cabpooling.employeecabpooling.model.enums.BookingStatus;
import com.cabpooling.employeecabpooling.repository.BookingRepository;
import com.cabpooling.employeecabpooling.repository.CabAssignmentRepository;
import com.cabpooling.employeecabpooling.repository.EscortAssignmentRepository;
import com.cabpooling.employeecabpooling.repository.PickupStopRepository;
import com.cabpooling.employeecabpooling.routing.DistanceProvider;
import com.cabpooling.employeecabpooling.routing.optimizer.TwoOptRouteOptimizer;
import com.cabpooling.employeecabpooling.routing.optimizer.TwoOptRouteOptimizer.OptimizedRouteResult;
import com.cabpooling.employeecabpooling.routing.validator.MaxRideTimeValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final TwoOptRouteOptimizer routeOptimizer;
    private final MaxRideTimeValidator maxRideTimeValidator;
    private final AllocationService allocationService;
    private final RoutingProperties routingProperties;

    @Transactional
    public CabAssignmentResponse handleCancellation(Long bookingId) {
        Optional<PickupStop> stopOpt = pickupStopRepository.findByBookingId(bookingId);
        if (stopOpt.isEmpty()) {
            return null;
        }

        PickupStop stopToRemove = stopOpt.get();
        CabAssignment assignment = stopToRemove.getCabAssignment();

        log.info("Handling cancellation of booking id={} from cab assignment id={}", bookingId, assignment.getId());

        List<Booking> remaining = new ArrayList<>();
        for (PickupStop s : assignment.getStops()) {
            if (s.getBooking() != null && !s.getBooking().getId().equals(bookingId)) {
                remaining.add(s.getBooking());
            }
        }

        if (remaining.isEmpty()) {
            assignment.getStops().clear();
            assignment.setTotalDistanceKm(0.0);
            assignment.setTotalDurationMinutes(0);
            if (Boolean.TRUE.equals(assignment.getHasEscort())) {
                escortAssignmentRepository.findByCabAssignmentId(assignment.getId())
                        .ifPresent(escortAssignmentRepository::delete);
                assignment.setHasEscort(false);
            }
            CabAssignment saved = cabAssignmentRepository.save(assignment);
            return allocationService.mapToResponse(saved);
        }

        // Local replan only — 2-opt on this cab; untouched assignments are never reshuffled
        reoptimizeAssignment(assignment, remaining);
        CabAssignment saved = cabAssignmentRepository.save(assignment);
        nightSafetyService.evaluateAndProtect(saved);
        saved = cabAssignmentRepository.save(saved);
        maxRideTimeValidator.validateAssignment(saved, routingProperties.getMaxRideMinutes());

        return allocationService.mapToResponse(saved);
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
        Office office = shift.getOffice();
        double maxDetourKm = routingProperties.getMaxDetourKm();

        List<CabAssignment> candidateAssignments = cabAssignmentRepository
                .findByAssignmentDateAndShiftId(date, shift.getId()).stream()
                .filter(a -> a.getStatus() == AssignmentStatus.PLANNED || a.getStatus() == AssignmentStatus.IN_PROGRESS)
                .toList();

        if (candidateAssignments.isEmpty()) {
            throw new BusinessRuleException("No active cab assignments available for shift on " + date);
        }

        CabAssignment bestAssignment = null;
        OptimizedRouteResult bestRoute = null;
        double minDetour = Double.MAX_VALUE;
        int bestInsertOrder = 1;

        for (CabAssignment candidate : candidateAssignments) {
            long currentPassengers = candidate.getStops().stream()
                    .filter(s -> s.getBooking() != null)
                    .count();

            if (currentPassengers >= candidate.getCab().getCapacity()) {
                continue;
            }

            List<Booking> proposed = new ArrayList<>();
            for (PickupStop s : candidate.getStops()) {
                if (s.getBooking() != null) {
                    proposed.add(s.getBooking());
                }
            }
            proposed.add(booking);

            double detour = estimateDetourKm(candidate, booking);
            if (detour > maxDetourKm) {
                continue;
            }

            try {
                OptimizedRouteResult routeResult = routeOptimizer.optimizeRoute(
                        office, proposed, shift.getShiftType(), distanceProvider);
                maxRideTimeValidator.validateRoute(routeResult, shift.getShiftType(),
                        routingProperties.getMaxRideMinutes());

                if (detour < minDetour) {
                    minDetour = detour;
                    bestAssignment = candidate;
                    bestRoute = routeResult;
                    bestInsertOrder = indexOfBookingInRoute(routeResult, booking.getId());
                }
            } catch (BusinessRuleException ex) {
                log.debug("Late insert into assignment {} rejected: {}", candidate.getId(), ex.getMessage());
            }
        }

        if (bestAssignment == null || bestRoute == null) {
            throw new BusinessRuleException(
                    "No suitable cab found with free seat, acceptable detour, and max-ride compliance for late booking");
        }

        allocationService.applyStopsToAssignment(bestAssignment, bestRoute, shift, date);
        bestAssignment.setTotalDistanceKm(bestRoute.getTotalDistanceKm());
        bestAssignment.setTotalDurationMinutes(bestRoute.getTotalDurationMinutes());
        bestAssignment = cabAssignmentRepository.save(bestAssignment);

        nightSafetyService.evaluateAndProtect(bestAssignment);
        bestAssignment = cabAssignmentRepository.save(bestAssignment);
        maxRideTimeValidator.validateAssignment(bestAssignment, routingProperties.getMaxRideMinutes());

        log.info("Inserted late booking id={} into cab assignment id={} (order {}) with detour {}km",
                bookingId, bestAssignment.getId(), bestInsertOrder, Math.round(minDetour * 100.0) / 100.0);

        return LateBookingInsertResponse.builder()
                .bookingId(booking.getId())
                .cabAssignmentId(bestAssignment.getId())
                .cabId(bestAssignment.getCab().getId())
                .cabLicensePlate(bestAssignment.getCab().getLicensePlate())
                .insertedAtStopOrder(bestInsertOrder)
                .detourKm(Math.round(minDetour * 100.0) / 100.0)
                .assignment(allocationService.mapToResponse(bestAssignment))
                .build();
    }

    private void reoptimizeAssignment(CabAssignment assignment, List<Booking> bookings) {
        Shift shift = assignment.getShift();
        Office office = shift.getOffice();
        OptimizedRouteResult routeResult = routeOptimizer.optimizeRoute(
                office, bookings, shift.getShiftType(), distanceProvider);
        maxRideTimeValidator.validateRoute(routeResult, shift.getShiftType(),
                routingProperties.getMaxRideMinutes());
        allocationService.applyStopsToAssignment(assignment, routeResult, shift, assignment.getAssignmentDate());
        assignment.setTotalDistanceKm(routeResult.getTotalDistanceKm());
        assignment.setTotalDurationMinutes(routeResult.getTotalDurationMinutes());
    }

    private double estimateDetourKm(CabAssignment candidate, Booking booking) {
        List<PickupStop> stops = candidate.getStops();
        if (stops.isEmpty()) {
            return 0.0;
        }

        double minDetour = Double.MAX_VALUE;
        for (int k = 0; k <= stops.size(); k++) {
            double detour = computeInsertionDetour(stops, k, booking.getPickupLatitude(), booking.getPickupLongitude());
            if (detour < minDetour) {
                minDetour = detour;
            }
        }
        return minDetour;
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

    private int indexOfBookingInRoute(OptimizedRouteResult route, Long bookingId) {
        List<TwoOptRouteOptimizer.OptimizedStop> stops = route.getStops();
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).getBooking() != null && bookingId.equals(stops.get(i).getBooking().getId())) {
                return i + 1;
            }
        }
        return 1;
    }
}
