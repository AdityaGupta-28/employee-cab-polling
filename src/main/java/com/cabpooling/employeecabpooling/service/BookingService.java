package com.cabpooling.employeecabpooling.service;

import com.cabpooling.employeecabpooling.dto.booking.BookingRequest;
import com.cabpooling.employeecabpooling.dto.booking.BookingResponse;
import com.cabpooling.employeecabpooling.exception.AccessForbiddenException;
import com.cabpooling.employeecabpooling.exception.BusinessRuleException;
import com.cabpooling.employeecabpooling.exception.DuplicateResourceException;
import com.cabpooling.employeecabpooling.exception.ResourceNotFoundException;
import com.cabpooling.employeecabpooling.model.entity.Booking;
import com.cabpooling.employeecabpooling.model.entity.Employee;
import com.cabpooling.employeecabpooling.model.entity.Shift;
import com.cabpooling.employeecabpooling.model.enums.BookingStatus;
import com.cabpooling.employeecabpooling.model.enums.Role;
import com.cabpooling.employeecabpooling.repository.BookingRepository;
import com.cabpooling.employeecabpooling.repository.EmployeeRepository;
import com.cabpooling.employeecabpooling.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftRepository shiftRepository;
    private final ReplanningService replanningService;

    @Transactional
    public BookingResponse create(BookingRequest request) {
        Employee employee = getCaller();
        Shift shift = getShiftOrThrow(request.getShiftId());

        // Idempotency check
        if (bookingRepository.existsByEmployeeIdAndShiftIdAndBookingDate(
                employee.getId(), shift.getId(), request.getBookingDate())) {
            throw new DuplicateResourceException(
                    "You already have a booking for this shift on " + request.getBookingDate());
        }

        // Cutoff window check: reject if booking is too close to shift start time
        validateBookingWindow(shift, request.getBookingDate());

        Booking booking = Booking.builder()
                .employee(employee)
                .shift(shift)
                .bookingDate(request.getBookingDate())
                .pickupLatitude(request.getPickupLatitude())
                .pickupLongitude(request.getPickupLongitude())
                .pickupAddress(request.getPickupAddress())
                .status(BookingStatus.CONFIRMED)
                .build();

        return toResponse(bookingRepository.save(booking));
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> findMyBookings(LocalDate date, Pageable pageable) {
        Employee employee = getCaller();
        Page<Booking> bookings = (date != null)
                ? bookingRepository.findByEmployeeIdAndBookingDate(employee.getId(), date, pageable)
                : bookingRepository.findByEmployeeId(employee.getId(), pageable);
        return bookings.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public BookingResponse findById(Long id) {
        Booking booking = getOrThrow(id);
        Employee caller = getCaller();

        if (!isAdmin(caller) && !booking.getEmployee().getId().equals(caller.getId())) {
            throw new AccessForbiddenException("You can only view your own bookings");
        }
        return toResponse(booking);
    }

    @Transactional
    public BookingResponse cancel(Long id) {
        Booking booking = getOrThrow(id);
        Employee caller = getCaller();

        if (!booking.getEmployee().getId().equals(caller.getId())) {
            throw new AccessForbiddenException("You can only cancel your own bookings");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleException(
                    "Only CONFIRMED bookings can be cancelled. Current status: " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.CANCELLED);
        Booking saved = bookingRepository.save(booking);

        // Live Dynamic Replanning: re-route affected cab assignment if assigned
        replanningService.handleCancellation(id);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> findAll(Pageable pageable) {
        return bookingRepository.findAll(pageable).map(this::toResponse);
    }

    // -------------------------------------------------------------------------
    // Business Rule: cutoff window
    // -------------------------------------------------------------------------

    private void validateBookingWindow(Shift shift, LocalDate bookingDate) {
        LocalDateTime shiftStart = LocalDateTime.of(bookingDate, shift.getStartTime());
        LocalDateTime cutoffDeadline = shiftStart.minusMinutes(shift.getCutoffMinutes());

        if (LocalDateTime.now().isAfter(cutoffDeadline)) {
            throw new BusinessRuleException(
                    "Booking window has closed. The cutoff for this shift was "
                            + shift.getCutoffMinutes() + " minutes before "
                            + shift.getStartTime() + " on " + bookingDate);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Booking getOrThrow(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));
    }

    private Shift getShiftOrThrow(Long id) {
        return shiftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found with id: " + id));
    }

    private Employee getCaller() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return employeeRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated employee not found"));
    }

    private boolean isAdmin(Employee employee) {
        return Role.ROLE_ADMIN.equals(employee.getRole());
    }

    public BookingResponse toResponse(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .employeeId(booking.getEmployee().getId())
                .employeeName(booking.getEmployee().getName())
                .shiftId(booking.getShift().getId())
                .shiftName(booking.getShift().getName())
                .bookingDate(booking.getBookingDate())
                .pickupAddress(booking.getPickupAddress())
                .pickupLatitude(booking.getPickupLatitude())
                .pickupLongitude(booking.getPickupLongitude())
                .status(booking.getStatus())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }
}
