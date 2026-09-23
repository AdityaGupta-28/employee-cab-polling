package com.cabpooling.employeecabpooling.repository;

import com.cabpooling.employeecabpooling.model.entity.Booking;
import com.cabpooling.employeecabpooling.model.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByEmployeeIdAndShiftIdAndBookingDate(Long employeeId, Long shiftId, LocalDate bookingDate);
    boolean existsByEmployeeIdAndShiftIdAndBookingDate(Long employeeId, Long shiftId, LocalDate bookingDate);
    List<Booking> findByBookingDateAndShiftId(LocalDate bookingDate, Long shiftId);
    List<Booking> findByBookingDateAndShiftIdAndStatus(LocalDate bookingDate, Long shiftId, BookingStatus status);
    List<Booking> findByEmployeeId(Long employeeId);
    List<Booking> findByEmployeeIdAndBookingDate(Long employeeId, LocalDate bookingDate);
    // Paginated variants used by BookingService
    Page<Booking> findByEmployeeId(Long employeeId, Pageable pageable);
    Page<Booking> findByEmployeeIdAndBookingDate(Long employeeId, LocalDate bookingDate, Pageable pageable);
}

