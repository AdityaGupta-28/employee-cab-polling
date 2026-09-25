package com.cabpooling.employeecabpooling.service;

import com.cabpooling.employeecabpooling.config.RoutingProperties;
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
import com.cabpooling.employeecabpooling.repository.BookingRepository;
import com.cabpooling.employeecabpooling.repository.CabAssignmentRepository;
import com.cabpooling.employeecabpooling.repository.CabRepository;
import com.cabpooling.employeecabpooling.repository.ShiftRepository;
import com.cabpooling.employeecabpooling.routing.DistanceProvider;
import com.cabpooling.employeecabpooling.routing.GeohashUtil;
import com.cabpooling.employeecabpooling.routing.optimizer.TwoOptRouteOptimizer;
import com.cabpooling.employeecabpooling.routing.optimizer.TwoOptRouteOptimizer.OptimizedRouteResult;
import com.cabpooling.employeecabpooling.routing.optimizer.TwoOptRouteOptimizer.OptimizedStop;
import com.cabpooling.employeecabpooling.routing.validator.MaxRideTimeValidator;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AllocationService {

    private final ShiftRepository shiftRepository;
    private final CabRepository cabRepository;
    private final BookingRepository bookingRepository;
    private final CabAssignmentRepository cabAssignmentRepository;
    private final DistanceProvider distanceProvider;
    private final TwoOptRouteOptimizer routeOptimizer;
    private final NightSafetyService nightSafetyService;
    private final MaxRideTimeValidator maxRideTimeValidator;
    private final RoutingProperties routingProperties;
    private final MeterRegistry meterRegistry;

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

        List<CabAssignment> existingPlanned = cabAssignmentRepository.findByAssignmentDateAndShiftId(date, shift.getId())
                .stream().filter(a -> a.getStatus() == AssignmentStatus.PLANNED).toList();
        if (!existingPlanned.isEmpty()) {
            cabAssignmentRepository.deleteAll(existingPlanned);
            cabAssignmentRepository.flush();
        }

        List<Cab> availableCabs = cabRepository.findByIsActive(true);
        if (availableCabs.isEmpty()) {
            throw new BusinessRuleException("No active cabs found in fleet. Please activate cabs in Fleet management.");
        }

        List<List<Booking>> clusters = clusterBookingsByProximity(confirmedBookings, availableCabs, office);

        List<CabAssignmentResponse> createdAssignmentResponses = new ArrayList<>();
        int cabIdx = 0;
        int assignedCount = 0;

        for (List<Booking> clusterBookings : clusters) {
            if (clusterBookings.isEmpty() || cabIdx >= availableCabs.size()) {
                break;
            }

            Cab cab = availableCabs.get(cabIdx++);

            if (clusterBookings.size() > cab.getCapacity()) {
                throw new BusinessRuleException(String.format(
                        "Seat capacity constraint violated: Cab %s capacity is %d, but %d passengers were assigned",
                        cab.getLicensePlate(), cab.getCapacity(), clusterBookings.size()));
            }

            CabAssignment assignment = CabAssignment.builder()
                    .cab(cab)
                    .shift(shift)
                    .assignmentDate(date)
                    .status(AssignmentStatus.PLANNED)
                    .hasEscort(false)
                    .stops(new ArrayList<>())
                    .build();

            assignment = cabAssignmentRepository.save(assignment);

            OptimizedRouteResult routeResult = routeOptimizer.optimizeRoute(
                    office, clusterBookings, shift.getShiftType(), distanceProvider);

            maxRideTimeValidator.validateRoute(routeResult, shift.getShiftType(),
                    routingProperties.getMaxRideMinutes());

            applyStopsToAssignment(assignment, routeResult, shift, date);
            assignment.setTotalDistanceKm(routeResult.getTotalDistanceKm());
            assignment.setTotalDurationMinutes(routeResult.getTotalDurationMinutes());
            assignment = cabAssignmentRepository.save(assignment);

            nightSafetyService.evaluateAndProtect(assignment);
            assignment = cabAssignmentRepository.save(assignment);

            // Re-validate after night reorder (escort path leaves distances unchanged)
            maxRideTimeValidator.validateAssignment(assignment, routingProperties.getMaxRideMinutes());

            assignedCount += clusterBookings.size();
            createdAssignmentResponses.add(mapToResponse(assignment));
        }

        meterRegistry.counter("cabpooling.allocations.auto_cluster").increment();
        meterRegistry.counter("cabpooling.allocations.bookings_assigned").increment(assignedCount);

        log.info("Auto-clustered {}/{} bookings into {} cabs for shift id={} on {}",
                assignedCount, confirmedBookings.size(), createdAssignmentResponses.size(), shift.getId(), date);

        return AutoClusterResponse.builder()
                .shiftId(shift.getId())
                .shiftName(shift.getName())
                .assignmentDate(date)
                .totalBookingsClustered(assignedCount)
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
            if (bookings.size() > assignment.getCab().getCapacity()) {
                throw new BusinessRuleException(String.format(
                        "Seat capacity constraint violated: Cab %s capacity is %d, but %d passengers are assigned",
                        assignment.getCab().getLicensePlate(), assignment.getCab().getCapacity(), bookings.size()));
            }

            OptimizedRouteResult routeResult = routeOptimizer.optimizeRoute(
                    office, bookings, shift.getShiftType(), distanceProvider);

            maxRideTimeValidator.validateRoute(routeResult, shift.getShiftType(),
                    routingProperties.getMaxRideMinutes());

            applyStopsToAssignment(assignment, routeResult, shift, date);
            assignment.setTotalDistanceKm(routeResult.getTotalDistanceKm());
            assignment.setTotalDurationMinutes(routeResult.getTotalDurationMinutes());
            assignment = cabAssignmentRepository.save(assignment);

            nightSafetyService.evaluateAndProtect(assignment);
            assignment = cabAssignmentRepository.save(assignment);
            maxRideTimeValidator.validateAssignment(assignment, routingProperties.getMaxRideMinutes());
        } else {
            assignment.getStops().clear();
            assignment.setTotalDistanceKm(0.0);
            assignment.setTotalDurationMinutes(0);
            assignment = cabAssignmentRepository.save(assignment);
        }

        log.info("Re-optimized cab assignment id={}", assignment.getId());
        return mapToResponse(assignment);
    }

    /**
     * Geohash-bucketed greedy clustering with capacity and max-cluster-radius (detour) constraints.
     * Time: O(N · C · B) where B is average bucket size ≪ N — not O(N²) all-pairs.
     */
    private List<List<Booking>> clusterBookingsByProximity(
            List<Booking> bookings, List<Cab> cabs, Office office) {

        int precision = routingProperties.getGeohashPrecision();
        double maxRadiusKm = routingProperties.getMaxClusterRadiusKm();

        Map<String, List<Booking>> buckets = new HashMap<>();
        for (Booking b : bookings) {
            String hash = GeohashUtil.encode(b.getPickupLatitude(), b.getPickupLongitude(), precision);
            buckets.computeIfAbsent(hash, k -> new ArrayList<>()).add(b);
        }

        Set<Long> assignedIds = new HashSet<>();
        List<Booking> pool = new ArrayList<>(bookings);
        List<List<Booking>> clusters = new ArrayList<>();
        int cabIndex = 0;

        while (!pool.isEmpty() && cabIndex < cabs.size()) {
            Cab cab = cabs.get(cabIndex++);
            int capacity = cab.getCapacity();
            List<Booking> currentCluster = new ArrayList<>();

            Booking seed = selectFarthestFromOffice(pool, office);
            currentCluster.add(seed);
            assignedIds.add(seed.getId());
            pool.remove(seed);

            while (currentCluster.size() < capacity && !pool.isEmpty()) {
                Booking best = findNearestEligibleCandidate(
                        currentCluster, pool, buckets, precision, maxRadiusKm);
                if (best == null) {
                    break; // no nearby rider left without a large detour
                }
                currentCluster.add(best);
                assignedIds.add(best.getId());
                pool.remove(best);
            }
            clusters.add(currentCluster);
        }

        long leftover = bookings.stream().filter(b -> !assignedIds.contains(b.getId())).count();
        if (leftover > 0) {
            log.warn("Seat capacity / proximity constraint: {} bookings could not be assigned — "
                    + "add more cabs or relax cluster radius.", leftover);
        }

        return clusters;
    }

    private Booking selectFarthestFromOffice(List<Booking> pool, Office office) {
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
        return seed;
    }

    private Booking findNearestEligibleCandidate(
            List<Booking> cluster,
            List<Booking> pool,
            Map<String, List<Booking>> buckets,
            int precision,
            double maxRadiusKm) {

        Booking seed = cluster.get(0);
        Booking lastAdded = cluster.get(cluster.size() - 1);

        // Spatial index: only inspect bookings in the seed's geohash neighbourhood
        Set<Long> candidateIds = new HashSet<>();
        for (String cell : GeohashUtil.encodeWithNeighbors(
                seed.getPickupLatitude(), seed.getPickupLongitude(), precision)) {
            List<Booking> cellBookings = buckets.get(cell);
            if (cellBookings != null) {
                for (Booking b : cellBookings) {
                    candidateIds.add(b.getId());
                }
            }
        }

        Booking closest = null;
        double minDist = Double.MAX_VALUE;

        for (Booking candidate : pool) {
            if (!candidateIds.contains(candidate.getId())) {
                continue;
            }
            double distFromSeed = distanceProvider.calculateDistanceKm(
                    seed.getPickupLatitude(), seed.getPickupLongitude(),
                    candidate.getPickupLatitude(), candidate.getPickupLongitude());
            if (distFromSeed > maxRadiusKm) {
                continue; // keep detours small — never mix far-apart areas
            }
            double distFromLast = distanceProvider.calculateDistanceKm(
                    lastAdded.getPickupLatitude(), lastAdded.getPickupLongitude(),
                    candidate.getPickupLatitude(), candidate.getPickupLongitude());
            if (distFromLast < minDist) {
                minDist = distFromLast;
                closest = candidate;
            }
        }

        // Fallback: if geohash neighbourhood yielded nothing but pool still has nearby riders
        // (edge of cell), scan pool once with radius filter only
        if (closest == null) {
            for (Booking candidate : pool) {
                double distFromSeed = distanceProvider.calculateDistanceKm(
                        seed.getPickupLatitude(), seed.getPickupLongitude(),
                        candidate.getPickupLatitude(), candidate.getPickupLongitude());
                if (distFromSeed > maxRadiusKm) {
                    continue;
                }
                double distFromLast = distanceProvider.calculateDistanceKm(
                        lastAdded.getPickupLatitude(), lastAdded.getPickupLongitude(),
                        candidate.getPickupLatitude(), candidate.getPickupLongitude());
                if (distFromLast < minDist) {
                    minDist = distFromLast;
                    closest = candidate;
                }
            }
        }

        return closest;
    }

    void applyStopsToAssignment(
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
            if (!optStops.isEmpty()) {
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

    CabAssignmentResponse mapToResponse(CabAssignment assignment) {
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
