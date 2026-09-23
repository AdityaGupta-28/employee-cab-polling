package com.cabpooling.employeecabpooling.service;

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
import com.cabpooling.employeecabpooling.model.enums.ShiftType;
import com.cabpooling.employeecabpooling.model.enums.StopType;
import com.cabpooling.employeecabpooling.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CabAssignmentService {

    private final CabAssignmentRepository cabAssignmentRepository;
    private final CabRepository cabRepository;
    private final ShiftRepository shiftRepository;
    private final BookingRepository bookingRepository;
    private final PickupStopRepository pickupStopRepository;

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

    @Transactional(readOnly = true)
    public CabAssignmentResponse findById(Long id) {
        CabAssignment assignment = cabAssignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cab assignment not found with id: " + id));
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

        // Validate one-directional lifecycle transition
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

    @Transactional
    public CabAssignmentResponse buildRoute(Long id) {
        CabAssignment assignment = cabAssignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cab assignment not found with id: " + id));

        Shift shift = assignment.getShift();
        Office office = shift.getOffice();
        LocalDate date = assignment.getAssignmentDate();

        List<Booking> confirmedBookings = bookingRepository.findByBookingDateAndShiftIdAndStatus(
                date, shift.getId(), BookingStatus.CONFIRMED);

        // Clear existing stops
        pickupStopRepository.deleteByCabAssignmentId(assignment.getId());
        pickupStopRepository.flush();
        assignment.getStops().clear();

        if (confirmedBookings.isEmpty()) {
            assignment.setTotalDistanceKm(0.0);
            assignment.setTotalDurationMinutes(0);
            CabAssignment saved = cabAssignmentRepository.save(assignment);
            return mapToResponse(saved);
        }

        // Limit bookings to cab capacity if needed
        int capacity = assignment.getCab().getCapacity();
        List<Booking> bookingsToRoute = confirmedBookings.size() > capacity
                ? confirmedBookings.subList(0, capacity)
                : new ArrayList<>(confirmedBookings);

        List<RouteStopData> orderedStops = new ArrayList<>();

        if (shift.getShiftType() == ShiftType.INBOUND) {
            // Nearest neighbour from office outward, then reverse so office is the final stop
            List<Booking> unvisited = new ArrayList<>(bookingsToRoute);
            List<Booking> orderedFromOffice = new ArrayList<>();
            double currLat = office.getLatitude();
            double currLon = office.getLongitude();

            while (!unvisited.isEmpty()) {
                Booking closest = null;
                double minDist = Double.MAX_VALUE;
                for (Booking b : unvisited) {
                    double dist = haversineKm(currLat, currLon, b.getPickupLatitude(), b.getPickupLongitude());
                    if (dist < minDist) {
                        minDist = dist;
                        closest = b;
                    }
                }
                orderedFromOffice.add(closest);
                unvisited.remove(closest);
                currLat = closest.getPickupLatitude();
                currLon = closest.getPickupLongitude();
            }

            // Reverse so farthest employee is picked up first
            Collections.reverse(orderedFromOffice);

            for (Booking b : orderedFromOffice) {
                orderedStops.add(new RouteStopData(
                        StopType.PICKUP,
                        b,
                        b.getPickupAddress(),
                        b.getPickupLatitude(),
                        b.getPickupLongitude()
                ));
            }

            // Office is the last stop
            orderedStops.add(new RouteStopData(
                    StopType.OFFICE,
                    null,
                    office.getAddress(),
                    office.getLatitude(),
                    office.getLongitude()
            ));

        } else {
            // OUTBOUND: Office is first stop, then drop off in nearest neighbour order
            orderedStops.add(new RouteStopData(
                    StopType.OFFICE,
                    null,
                    office.getAddress(),
                    office.getLatitude(),
                    office.getLongitude()
            ));

            List<Booking> unvisited = new ArrayList<>(bookingsToRoute);
            double currLat = office.getLatitude();
            double currLon = office.getLongitude();

            while (!unvisited.isEmpty()) {
                Booking closest = null;
                double minDist = Double.MAX_VALUE;
                for (Booking b : unvisited) {
                    double dist = haversineKm(currLat, currLon, b.getPickupLatitude(), b.getPickupLongitude());
                    if (dist < minDist) {
                        minDist = dist;
                        closest = b;
                    }
                }
                orderedStops.add(new RouteStopData(
                        StopType.DROPOFF,
                        closest,
                        closest.getPickupAddress(),
                        closest.getPickupLatitude(),
                        closest.getPickupLongitude()
                ));
                unvisited.remove(closest);
                currLat = closest.getPickupLatitude();
                currLon = closest.getPickupLongitude();
            }
        }

        // Calculate distance, duration, and planned times
        double totalDistanceKm = 0.0;
        int totalDurationMinutes = 0;
        double[] legDistances = new double[orderedStops.size()];
        int[] legDurations = new int[orderedStops.size()];

        for (int i = 0; i < orderedStops.size(); i++) {
            if (i == 0) {
                legDistances[i] = 0.0;
                legDurations[i] = 0;
            } else {
                RouteStopData prev = orderedStops.get(i - 1);
                RouteStopData curr = orderedStops.get(i);
                double d = haversineKm(prev.lat, prev.lon, curr.lat, curr.lon);
                int dur = (int) Math.round((d / 30.0) * 60.0);
                if (d > 0.05 && dur == 0) {
                    dur = 1;
                }
                legDistances[i] = d;
                legDurations[i] = dur;
                totalDistanceKm += d;
                totalDurationMinutes += dur;
            }
        }

        // Calculate planned times
        OffsetDateTime[] plannedTimes = new OffsetDateTime[orderedStops.size()];
        OffsetDateTime now = OffsetDateTime.now();

        if (shift.getShiftType() == ShiftType.INBOUND) {
            // Arrival at office at shift.startTime
            OffsetDateTime officeArrival = date.atTime(shift.getStartTime()).atOffset(now.getOffset());
            OffsetDateTime routeStart = officeArrival.minusMinutes(totalDurationMinutes);
            OffsetDateTime currentPlanned = routeStart;
            for (int i = 0; i < orderedStops.size(); i++) {
                if (i > 0) {
                    currentPlanned = currentPlanned.plusMinutes(legDurations[i]);
                }
                plannedTimes[i] = currentPlanned;
            }
            plannedTimes[orderedStops.size() - 1] = officeArrival;
        } else {
            // Departure from office at shift.endTime
            OffsetDateTime officeDeparture = date.atTime(shift.getEndTime()).atOffset(now.getOffset());
            OffsetDateTime currentPlanned = officeDeparture;
            for (int i = 0; i < orderedStops.size(); i++) {
                if (i > 0) {
                    currentPlanned = currentPlanned.plusMinutes(legDurations[i]);
                }
                plannedTimes[i] = currentPlanned;
            }
        }

        // Persist PickupStop records
        List<PickupStop> persistedStops = new ArrayList<>();
        for (int i = 0; i < orderedStops.size(); i++) {
            RouteStopData stopData = orderedStops.get(i);
            PickupStop stop = PickupStop.builder()
                    .cabAssignment(assignment)
                    .booking(stopData.booking)
                    .stopOrder(i + 1)
                    .stopType(stopData.type)
                    .latitude(stopData.lat)
                    .longitude(stopData.lon)
                    .address(stopData.address)
                    .distanceFromPreviousKm(legDistances[i])
                    .durationFromPreviousMinutes(legDurations[i])
                    .plannedTime(plannedTimes[i])
                    .build();
            persistedStops.add(stop);
        }

        assignment.getStops().addAll(persistedStops);
        assignment.setTotalDistanceKm(Math.round(totalDistanceKm * 100.0) / 100.0);
        assignment.setTotalDurationMinutes(totalDurationMinutes);

        CabAssignment saved = cabAssignmentRepository.save(assignment);
        log.info("Built route for cab assignment id={} with {} stops, totalDistance={}km, totalDuration={}min",
                saved.getId(), persistedStops.size(), saved.getTotalDistanceKm(), saved.getTotalDurationMinutes());
        return mapToResponse(saved);
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(R * c * 100.0) / 100.0;
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

    private record RouteStopData(
            StopType type,
            Booking booking,
            String address,
            double lat,
            double lon
    ) {}
}
