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
import java.util.Optional;

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

        // Idempotency: same employee + shift + date must never occupy two seats
        Optional<Booking> existing = bookingRepository.findByEmployeeIdAndShiftIdAndBookingDate(
                employee.getId(), shift.getId(), request.getBookingDate());

        if (existing.isPresent()) {
            Booking prior = existing.get();
            if (prior.getStatus() == BookingStatus.CONFIRMED) {
                throw new DuplicateResourceException(
                        "You already have an active booking for this shift on " + request.getBookingDate());
            }
            // Reactivate a previously cancelled booking instead of inserting a duplicate row
            validateBookingWindow(shift, request.getBookingDate());
            prior.setStatus(BookingStatus.CONFIRMED);
            prior.setPickupLatitude(request.getPickupLatitude());
            prior.setPickupLongitude(request.getPickupLongitude());
            prior.setPickupAddress(request.getPickupAddress());
            return toResponse(bookingRepository.save(prior));
        }

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

        // Live Dynamic Replanning: re-route affected cab only — other cabs untouched
        replanningService.handleCancellation(id);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> findAll(Pageable pageable) {
        return bookingRepository.findAll(pageable).map(this::toResponse);
    }

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
