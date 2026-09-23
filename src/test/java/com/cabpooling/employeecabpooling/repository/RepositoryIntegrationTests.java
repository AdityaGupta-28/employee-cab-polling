package com.cabpooling.employeecabpooling.repository;

import com.cabpooling.employeecabpooling.model.entity.*;
import com.cabpooling.employeecabpooling.model.enums.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RepositoryIntegrationTests {

    @Autowired
    private OfficeRepository officeRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private CabRepository cabRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private CabAssignmentRepository cabAssignmentRepository;

    @Autowired
    private PickupStopRepository pickupStopRepository;

    @Autowired
    private EscortAssignmentRepository escortAssignmentRepository;

    @Test
    @DisplayName("Should successfully persist and query core entities with relational integrity")
    void testEntityPersistenceAndRelationalIntegrity() {
        // 1. Create and persist Office
        Office office = officeRepository.save(Office.builder()
                .name("MoveInSync HQ Bangalore")
                .address("Outer Ring Road, Bellandur, Bangalore")
                .latitude(12.9279)
                .longitude(77.6811)
                .build());
        assertThat(office.getId()).isNotNull();

        // 2. Create and persist Employee
        Employee employee = employeeRepository.save(Employee.builder()
                .email("aditya.test@moveinsync.com")
                .passwordHash("$2a$10$encodedHashDummyValue")
                .name("Aditya Test")
                .role(Role.ROLE_EMPLOYEE)
                .gender(Gender.MALE)
                .phoneNumber("+919876543210")
                .homeAddress("HSR Layout Sector 1, Bangalore")
                .homeLatitude(12.9121)
                .homeLongitude(77.6446)
                .build());
        assertThat(employee.getId()).isNotNull();
        assertThat(employeeRepository.existsByEmail("aditya.test@moveinsync.com")).isTrue();

        // 3. Create and persist Cab
        Cab cab = cabRepository.save(Cab.builder()
                .licensePlate("KA-01-AB-1234")
                .model("Toyota Innova")
                .capacity(6)
                .driverName("Ramesh Kumar")
                .driverPhone("+919123456780")
                .isActive(true)
                .build());
        assertThat(cab.getId()).isNotNull();
        assertThat(cabRepository.findByIsActiveTrue()).isNotEmpty();

        // 4. Create and persist Shift
        Shift shift = shiftRepository.save(Shift.builder()
                .office(office)
                .name("Morning Login 09:00")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(18, 0))
                .shiftType(ShiftType.INBOUND)
                .cutoffMinutes(120)
                .build());
        assertThat(shift.getId()).isNotNull();

        // 5. Create and persist Booking
        LocalDate today = LocalDate.now();
        Booking booking = bookingRepository.save(Booking.builder()
                .employee(employee)
                .shift(shift)
                .bookingDate(today)
                .pickupLatitude(12.9121)
                .pickupLongitude(77.6446)
                .pickupAddress("HSR Layout Sector 1, Bangalore")
                .status(BookingStatus.CONFIRMED)
                .build());
        assertThat(booking.getId()).isNotNull();

        // Verify query by employee, shift, and date
        Optional<Booking> foundBooking = bookingRepository.findByEmployeeIdAndShiftIdAndBookingDate(
                employee.getId(), shift.getId(), today);
        assertThat(foundBooking).isPresent();
        assertThat(foundBooking.get().getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        // 6. Create and persist CabAssignment
        CabAssignment assignment = cabAssignmentRepository.save(CabAssignment.builder()
                .cab(cab)
                .shift(shift)
                .assignmentDate(today)
                .status(AssignmentStatus.PLANNED)
                .totalDistanceKm(14.5)
                .totalDurationMinutes(40)
                .hasEscort(true)
                .build());
        assertThat(assignment.getId()).isNotNull();

        // 7. Create and persist PickupStop
        PickupStop stop = pickupStopRepository.save(PickupStop.builder()
                .cabAssignment(assignment)
                .booking(booking)
                .stopOrder(1)
                .stopType(StopType.PICKUP)
                .latitude(12.9121)
                .longitude(77.6446)
                .address("HSR Layout Sector 1, Bangalore")
                .distanceFromPreviousKm(0.0)
                .durationFromPreviousMinutes(0)
                .build());
        assertThat(stop.getId()).isNotNull();

        // 8. Create and persist EscortAssignment
        EscortAssignment escort = escortAssignmentRepository.save(EscortAssignment.builder()
                .cabAssignment(assignment)
                .escortName("Suresh Security")
                .escortContact("+919988776655")
                .status(EscortStatus.ASSIGNED)
                .build());
        assertThat(escort.getId()).isNotNull();
        assertThat(escortAssignmentRepository.findByCabAssignmentId(assignment.getId())).isPresent();
    }
}
