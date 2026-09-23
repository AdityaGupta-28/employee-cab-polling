package com.cabpooling.employeecabpooling.service;

import com.cabpooling.employeecabpooling.dto.allocation.AutoClusterRequest;
import com.cabpooling.employeecabpooling.dto.allocation.AutoClusterResponse;
import com.cabpooling.employeecabpooling.dto.assignment.CabAssignmentResponse;
import com.cabpooling.employeecabpooling.dto.assignment.PickupStopResponse;
import com.cabpooling.employeecabpooling.exception.BusinessRuleException;
import com.cabpooling.employeecabpooling.exception.ResourceNotFoundException;
import com.cabpooling.employeecabpooling.model.entity.*;
import com.cabpooling.employeecabpooling.model.enums.AssignmentStatus;
import com.cabpooling.employeecabpooling.model.enums.BookingStatus;
import com.cabpooling.employeecabpooling.model.enums.ShiftType;
import com.cabpooling.employeecabpooling.model.enums.StopType;
import com.cabpooling.employeecabpooling.repository.BookingRepository;
import com.cabpooling.employeecabpooling.repository.CabAssignmentRepository;
import com.cabpooling.employeecabpooling.repository.CabRepository;
import com.cabpooling.employeecabpooling.repository.PickupStopRepository;
import com.cabpooling.employeecabpooling.repository.ShiftRepository;
import com.cabpooling.employeecabpooling.routing.DistanceProvider;
import com.cabpooling.employeecabpooling.routing.optimizer.TwoOptRouteOptimizer;
import com.cabpooling.employeecabpooling.routing.optimizer.TwoOptRouteOptimizer.OptimizedRouteResult;
import com.cabpooling.employeecabpooling.routing.optimizer.TwoOptRouteOptimizer.OptimizedStop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AllocationService {

    private final ShiftRepository shiftRepository;
    private final CabRepository cabRepository;
    private final BookingRepository bookingRepository;
    private final CabAssignmentRepository cabAssignmentRepository;
    private final PickupStopRepository pickupStopRepository;
    private final DistanceProvider distanceProvider;
    private final TwoOptRouteOptimizer routeOptimizer;
    private final NightSafetyService nightSafetyService;

    @Transactional
    public AutoClusterResponse autoCluster(AutoClusterRequest request) {
        Shift shift = shiftRepository.findById(request.getShiftId())
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found with id: " + request.getShiftId()));

        Office office = shift.getOffice();
        LocalDate date = request.getAssignmentDate();

        List<Booking> confirmedBookings = bookingRepository.findByBookingDateAndShiftIdAndStatus(
                date, shift.getId(), BookingStatus.CONFIRMED);

        if (confirmedBookings.isEmpty()) {
            return AutoClusterResponse.builder()
                    .shiftId(shift.getId())
                    .shiftName(shift.getName())
                    .assignmentDate(date)
                    .totalBookingsClustered(0)
                    .totalCabsAssigned(0)
                    .assignments(List.of())
                    .build();
        }

        // Available active cabs (not already assigned to this shift and date)
        List<Cab> activeCabs = cabRepository.findByIsActive(true);
        List<CabAssignment> existingAssignments = cabAssignmentRepository.findByAssignmentDateAndShiftId(date, shift.getId());
        List<Long> assignedCabIds = existingAssignments.stream().map(a -> a.getCab().getId()).toList();

        List<Cab> availableCabs = activeCabs.stream()
                .filter(c -> !assignedCabIds.contains(c.getId()))
                .toList();

        if (availableCabs.isEmpty()) {
            throw new BusinessRuleException("No available active cabs found for shift on " + date);
        }

        // Spatial Greedy Clustering: Group bookings by proximity
        List<List<Booking>> clusters = clusterBookingsByProximity(confirmedBookings, availableCabs, office);

        List<CabAssignmentResponse> createdAssignmentResponses = new ArrayList<>();
        int cabIdx = 0;

        for (List<Booking> clusterBookings : clusters) {
            if (clusterBookings.isEmpty() || cabIdx >= availableCabs.size()) {
                break;
            }

            Cab cab = availableCabs.get(cabIdx++);
            CabAssignment assignment = CabAssignment.builder()
                    .cab(cab)
                    .shift(shift)
                    .assignmentDate(date)
                    .status(AssignmentStatus.PLANNED)
                    .hasEscort(false)
                    .stops(new ArrayList<>())
                    .build();

            assignment = cabAssignmentRepository.save(assignment);

            // Optimize route using 2-Opt and distance provider
            OptimizedRouteResult routeResult = routeOptimizer.optimizeRoute(
                    office, clusterBookings, shift.getShiftType(), distanceProvider);

            applyStopsToAssignment(assignment, routeResult, shift, date);
            assignment.setTotalDistanceKm(routeResult.getTotalDistanceKm());
            assignment.setTotalDurationMinutes(routeResult.getTotalDurationMinutes());

            assignment = cabAssignmentRepository.save(assignment);

            // Apply Night Safety rule (auto re-order or provision escort)
            nightSafetyService.evaluateAndProtect(assignment);

            createdAssignmentResponses.add(mapToResponse(assignment));
        }

        log.info("Auto-clustered {} bookings into {} cabs for shift id={} on {}",
                confirmedBookings.size(), createdAssignmentResponses.size(), shift.getId(), date);

        return AutoClusterResponse.builder()
                .shiftId(shift.getId())
                .shiftName(shift.getName())
                .assignmentDate(date)
                .totalBookingsClustered(confirmedBookings.size())
                .totalCabsAssigned(createdAssignmentResponses.size())
                .assignments(createdAssignmentResponses)
                .build();
    }

    @Transactional
    public CabAssignmentResponse optimizeAssignment(Long cabAssignmentId) {
        CabAssignment assignment = cabAssignmentRepository.findById(cabAssignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Cab assignment not found with id: " + cabAssignmentId));

        Shift shift = assignment.getShift();
        Office office = shift.getOffice();
        LocalDate date = assignment.getAssignmentDate();

        List<Booking> bookings = assignment.getStops().stream()
                .filter(s -> s.getBooking() != null)
                .map(PickupStop::getBooking)
                .toList();

        if (!bookings.isEmpty()) {
            OptimizedRouteResult routeResult = routeOptimizer.optimizeRoute(
                    office, bookings, shift.getShiftType(), distanceProvider);

            applyStopsToAssignment(assignment, routeResult, shift, date);
            assignment.setTotalDistanceKm(routeResult.getTotalDistanceKm());
            assignment.setTotalDurationMinutes(routeResult.getTotalDurationMinutes());
            assignment = cabAssignmentRepository.save(assignment);

            nightSafetyService.evaluateAndProtect(assignment);
        } else {
            assignment.getStops().clear();
            assignment.setTotalDistanceKm(0.0);
            assignment.setTotalDurationMinutes(0);
            assignment = cabAssignmentRepository.save(assignment);
        }

        log.info("Re-optimized cab assignment id={}", assignment.getId());
        return mapToResponse(assignment);
    }

    private List<List<Booking>> clusterBookingsByProximity(
            List<Booking> bookings, List<Cab> cabs, Office office) {

        List<List<Booking>> clusters = new ArrayList<>();
        List<Booking> pool = new ArrayList<>(bookings);
        int cabIndex = 0;

        while (!pool.isEmpty() && cabIndex < cabs.size()) {
            Cab cab = cabs.get(cabIndex++);
            int capacity = cab.getCapacity();
            List<Booking> currentCluster = new ArrayList<>();

            // Find farthest booking from office as seed for this cab
            Booking seed = pool.get(0);
            double maxSeedDist = -1.0;
            for (Booking b : pool) {
                double d = distanceProvider.calculateDistanceKm(
                        office.getLatitude(), office.getLongitude(),
                        b.getPickupLatitude(), b.getPickupLongitude());
                if (d > maxSeedDist) {
                    maxSeedDist = d;
                    seed = b;
                }
            }

            currentCluster.add(seed);
            pool.remove(seed);

            // Fill cab with nearest neighbours to current cluster centroid
            while (currentCluster.size() < capacity && !pool.isEmpty()) {
                Booking lastAdded = currentCluster.get(currentCluster.size() - 1);
                Booking closest = null;
                double minDist = Double.MAX_VALUE;

                for (Booking candidate : pool) {
                    double dist = distanceProvider.calculateDistanceKm(
                            lastAdded.getPickupLatitude(), lastAdded.getPickupLongitude(),
                            candidate.getPickupLatitude(), candidate.getPickupLongitude());
                    if (dist < minDist) {
                        minDist = dist;
                        closest = candidate;
                    }
                }

                if (closest != null) {
                    currentCluster.add(closest);
                    pool.remove(closest);
                }
            }
            clusters.add(currentCluster);
        }

        // If bookings still remain but cabs ran out, place into last cluster or log warning
        if (!pool.isEmpty() && !clusters.isEmpty()) {
            clusters.get(clusters.size() - 1).addAll(pool);
        }

        return clusters;
    }

    private void applyStopsToAssignment(
            CabAssignment assignment,
            OptimizedRouteResult routeResult,
            Shift shift,
            LocalDate date) {

        List<OptimizedStop> optStops = routeResult.getStops();
        List<PickupStop> existingStops = assignment.getStops();
        OffsetDateTime now = OffsetDateTime.now();

        OffsetDateTime[] plannedTimes = new OffsetDateTime[optStops.size()];
        if (shift.getShiftType() == ShiftType.INBOUND) {
            OffsetDateTime officeArrival = date.atTime(shift.getStartTime()).atOffset(now.getOffset());
            OffsetDateTime current = officeArrival.minusMinutes(routeResult.getTotalDurationMinutes());
            for (int i = 0; i < optStops.size(); i++) {
                if (i > 0) {
                    current = current.plusMinutes(optStops.get(i).getDurationFromPreviousMinutes());
                }
                plannedTimes[i] = current;
            }
            if (optStops.size() > 0) {
                plannedTimes[optStops.size() - 1] = officeArrival;
            }
        } else {
            OffsetDateTime officeDeparture = date.atTime(shift.getEndTime()).atOffset(now.getOffset());
            OffsetDateTime current = officeDeparture;
            for (int i = 0; i < optStops.size(); i++) {
                if (i > 0) {
                    current = current.plusMinutes(optStops.get(i).getDurationFromPreviousMinutes());
                }
                plannedTimes[i] = current;
            }
        }

        for (int i = 0; i < optStops.size(); i++) {
            OptimizedStop opt = optStops.get(i);
            if (i < existingStops.size()) {
                PickupStop existing = existingStops.get(i);
                existing.setBooking(opt.getBooking());
                existing.setStopOrder(i + 1);
                existing.setStopType(opt.getStopType());
                existing.setLatitude(opt.getLatitude());
                existing.setLongitude(opt.getLongitude());
                existing.setAddress(opt.getAddress());
                existing.setDistanceFromPreviousKm(opt.getDistanceFromPreviousKm());
                existing.setDurationFromPreviousMinutes(opt.getDurationFromPreviousMinutes());
                existing.setPlannedTime(plannedTimes[i]);
            } else {
                PickupStop newStop = PickupStop.builder()
                        .cabAssignment(assignment)
                        .booking(opt.getBooking())
                        .stopOrder(i + 1)
                        .stopType(opt.getStopType())
                        .latitude(opt.getLatitude())
                        .longitude(opt.getLongitude())
                        .address(opt.getAddress())
                        .distanceFromPreviousKm(opt.getDistanceFromPreviousKm())
                        .durationFromPreviousMinutes(opt.getDurationFromPreviousMinutes())
                        .plannedTime(plannedTimes[i])
                        .build();
                existingStops.add(newStop);
            }
        }

        while (existingStops.size() > optStops.size()) {
            existingStops.remove(existingStops.size() - 1);
        }
    }

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
