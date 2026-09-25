package com.cabpooling.employeecabpooling.service;

import com.cabpooling.employeecabpooling.config.RoutingProperties;
import com.cabpooling.employeecabpooling.dto.assignment.AssignmentStatusRequest;
import com.cabpooling.employeecabpooling.dto.assignment.CabAssignmentRequest;
import com.cabpooling.employeecabpooling.dto.assignment.CabAssignmentResponse;
import com.cabpooling.employeecabpooling.dto.assignment.PickupStopResponse;
import com.cabpooling.employeecabpooling.exception.BusinessRuleException;
import com.cabpooling.employeecabpooling.exception.DuplicateResourceException;
import com.cabpooling.employeecabpooling.exception.ResourceNotFoundException;
import com.cabpooling.employeecabpooling.model.entity.*;
import com.cabpooling.employeecabpooling.model.enums.AssignmentStatus;
import com.cabpooling.employeecabpooling.model.enums.BookingStatus;
import com.cabpooling.employeecabpooling.model.enums.Role;
import com.cabpooling.employeecabpooling.repository.*;
import com.cabpooling.employeecabpooling.routing.DistanceProvider;
import com.cabpooling.employeecabpooling.routing.optimizer.TwoOptRouteOptimizer;
import com.cabpooling.employeecabpooling.routing.optimizer.TwoOptRouteOptimizer.OptimizedRouteResult;
import com.cabpooling.employeecabpooling.routing.validator.MaxRideTimeValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CabAssignmentService {

    private final CabAssignmentRepository cabAssignmentRepository;
    private final CabRepository cabRepository;
    private final ShiftRepository shiftRepository;
    private final BookingRepository bookingRepository;
    private final EmployeeRepository employeeRepository;
    private final AllocationService allocationService;
    private final TwoOptRouteOptimizer routeOptimizer;
    private final DistanceProvider distanceProvider;
    private final MaxRideTimeValidator maxRideTimeValidator;
    private final NightSafetyService nightSafetyService;
    private final RoutingProperties routingProperties;

    @Transactional
    public CabAssignmentResponse create(CabAssignmentRequest request) {
        Cab cab = cabRepository.findById(request.getCabId())
                .orElseThrow(() -> new ResourceNotFoundException("Cab not found with id: " + request.getCabId()));

        if (!Boolean.TRUE.equals(cab.getIsActive())) {
            throw new BusinessRuleException("Cannot assign inactive cab with id: " + cab.getId());
        }

        Shift shift = shiftRepository.findById(request.getShiftId())
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found with id: " + request.getShiftId()));

        if (cabAssignmentRepository.existsByCabIdAndShiftIdAndAssignmentDate(cab.getId(), shift.getId(), request.getAssignmentDate())) {
            throw new DuplicateResourceException("Cab assignment already exists for cab " + cab.getId()
                    + ", shift " + shift.getId() + " on " + request.getAssignmentDate());
        }

        CabAssignment assignment = CabAssignment.builder()
                .cab(cab)
                .shift(shift)
                .assignmentDate(request.getAssignmentDate())
                .status(AssignmentStatus.PLANNED)
                .totalDistanceKm(0.0)
                .totalDurationMinutes(0)
                .hasEscort(false)
                .stops(new ArrayList<>())
                .build();

        CabAssignment saved = cabAssignmentRepository.save(assignment);
        log.info("Created cab assignment id={} for cab={} and shift={}", saved.getId(), cab.getId(), shift.getId());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CabAssignmentResponse> findAll(LocalDate date, Long shiftId) {
        List<CabAssignment> assignments;
        if (date != null && shiftId != null) {
            assignments = cabAssignmentRepository.findByAssignmentDateAndShiftId(date, shiftId);
        } else if (date != null) {
            assignments = cabAssignmentRepository.findByAssignmentDate(date);
        } else if (shiftId != null) {
            assignments = cabAssignmentRepository.findByShiftId(shiftId);
        } else {
            assignments = cabAssignmentRepository.findAll();
        }
        return assignments.stream().map(this::mapToResponse).toList();
    }

    /**
     * Employee-facing: return cab assignments where the caller has a pickup/dropoff stop,
     * including planned pickup ETAs.
     */
    @Transactional(readOnly = true)
    public List<CabAssignmentResponse> findMyAssignments(LocalDate date) {
        Employee caller = getCaller();
        List<CabAssignment> all = (date != null)
                ? cabAssignmentRepository.findByAssignmentDate(date)
                : cabAssignmentRepository.findAll();

        return all.stream()
                .filter(a -> a.getStops() != null && a.getStops().stream()
                        .anyMatch(s -> s.getBooking() != null
                                && s.getBooking().getEmployee() != null
                                && s.getBooking().getEmployee().getId().equals(caller.getId())))
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CabAssignmentResponse findById(Long id) {
        CabAssignment assignment = cabAssignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cab assignment not found with id: " + id));

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            Employee caller = getCaller();
            if (!Role.ROLE_ADMIN.equals(caller.getRole())) {
                boolean ownsStop = assignment.getStops() != null && assignment.getStops().stream()
                        .anyMatch(s -> s.getBooking() != null
                                && s.getBooking().getEmployee() != null
                                && s.getBooking().getEmployee().getId().equals(caller.getId()));
                if (!ownsStop) {
                    throw new ResourceNotFoundException("Cab assignment not found with id: " + id);
                }
            }
        }
        return mapToResponse(assignment);
    }

    @Transactional
    public CabAssignmentResponse updateStatus(Long id, AssignmentStatusRequest request) {
        CabAssignment assignment = cabAssignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cab assignment not found with id: " + id));

        AssignmentStatus current = assignment.getStatus();
        AssignmentStatus target = request.getStatus();

        if (current == target) {
            return mapToResponse(assignment);
        }

        boolean valid = false;
        if (current == AssignmentStatus.PLANNED) {
            valid = (target == AssignmentStatus.IN_PROGRESS || target == AssignmentStatus.CANCELLED);
        } else if (current == AssignmentStatus.IN_PROGRESS) {
            valid = (target == AssignmentStatus.COMPLETED || target == AssignmentStatus.CANCELLED);
        }

        if (!valid) {
            throw new BusinessRuleException("Invalid status transition from " + current + " to " + target);
        }

        assignment.setStatus(target);
        CabAssignment updated = cabAssignmentRepository.save(assignment);
        log.info("Updated cab assignment id={} status to {}", updated.getId(), target);
        return mapToResponse(updated);
    }

    @Transactional
    public void delete(Long id) {
        CabAssignment assignment = cabAssignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cab assignment not found with id: " + id));
        cabAssignmentRepository.delete(assignment);
        log.info("Deleted cab assignment id={}", id);
    }

    /**
     * Builds / rebuilds the route for an assignment using NN + 2-opt.
     * If the assignment already has passengers, re-optimizes those.
     * If empty, fills from unassigned confirmed bookings (respecting capacity) then optimizes.
     */
    @Transactional
    public CabAssignmentResponse buildRoute(Long id) {
        CabAssignment assignment = cabAssignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cab assignment not found with id: " + id));

        Shift shift = assignment.getShift();
        Office office = shift.getOffice();
        LocalDate date = assignment.getAssignmentDate();
        int capacity = assignment.getCab().getCapacity();

        List<Booking> bookingsToRoute = assignment.getStops().stream()
                .filter(s -> s.getBooking() != null)
                .map(PickupStop::getBooking)
                .toList();

        if (bookingsToRoute.isEmpty()) {
            Set<Long> alreadyAssigned = new HashSet<>();
            for (CabAssignment other : cabAssignmentRepository.findByAssignmentDateAndShiftId(date, shift.getId())) {
                if (other.getId().equals(assignment.getId())) {
                    continue;
                }
                for (PickupStop s : other.getStops()) {
                    if (s.getBooking() != null) {
                        alreadyAssigned.add(s.getBooking().getId());
                    }
                }
            }

            bookingsToRoute = bookingRepository
                    .findByBookingDateAndShiftIdAndStatus(date, shift.getId(), BookingStatus.CONFIRMED)
                    .stream()
                    .filter(b -> !alreadyAssigned.contains(b.getId()))
                    .limit(capacity)
                    .toList();
        }

        if (bookingsToRoute.size() > capacity) {
            throw new BusinessRuleException(String.format(
                    "Seat capacity constraint violated: Cab %s holds %d, but %d passengers were requested",
                    assignment.getCab().getLicensePlate(), capacity, bookingsToRoute.size()));
        }

        if (bookingsToRoute.isEmpty()) {
            assignment.getStops().clear();
            assignment.setTotalDistanceKm(0.0);
            assignment.setTotalDurationMinutes(0);
            CabAssignment saved = cabAssignmentRepository.save(assignment);
            return mapToResponse(saved);
        }

        OptimizedRouteResult routeResult = routeOptimizer.optimizeRoute(
                office, bookingsToRoute, shift.getShiftType(), distanceProvider);
        maxRideTimeValidator.validateRoute(routeResult, shift.getShiftType(),
                routingProperties.getMaxRideMinutes());

        allocationService.applyStopsToAssignment(assignment, routeResult, shift, date);
        assignment.setTotalDistanceKm(routeResult.getTotalDistanceKm());
        assignment.setTotalDurationMinutes(routeResult.getTotalDurationMinutes());
        assignment = cabAssignmentRepository.save(assignment);

        nightSafetyService.evaluateAndProtect(assignment);
        assignment = cabAssignmentRepository.save(assignment);
        maxRideTimeValidator.validateAssignment(assignment, routingProperties.getMaxRideMinutes());

        log.info("Built route for cab assignment id={} with {} stops", assignment.getId(), assignment.getStops().size());
        return mapToResponse(assignment);
    }

    private Employee getCaller() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return employeeRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated employee not found"));
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
