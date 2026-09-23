package com.cabpooling.employeecabpooling.config;

import com.cabpooling.employeecabpooling.model.entity.*;
import com.cabpooling.employeecabpooling.model.enums.*;
import com.cabpooling.employeecabpooling.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final OfficeRepository officeRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftRepository shiftRepository;
    private final CabRepository cabRepository;
    private final BookingRepository bookingRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (officeRepository.count() > 0) {
            log.info("Demo data already present. Skipping DataInitializer.");
            return;
        }

        log.info("🌱 Seeding realistic MoveInSync Bangalore demo data...");

        // 1. Corporate Office Hubs
        Office bellandurHq = officeRepository.save(Office.builder()
                .name("MoveInSync Tech Park (Bellandur HQ)")
                .address("Outer Ring Road, Bellandur, Bengaluru, Karnataka 560103")
                .latitude(12.9279)
                .longitude(77.6771)
                .build());

        officeRepository.save(Office.builder()
                .name("Electronic City Campus")
                .address("Hosur Rd, Phase 1, Electronic City, Bengaluru, Karnataka 560100")
                .latitude(12.8452)
                .longitude(77.6602)
                .build());

        officeRepository.save(Office.builder()
                .name("Whitefield SEZ Hub")
                .address("ITPL Main Rd, KIADB Export Promotion Area, Whitefield, Bengaluru 560066")
                .latitude(12.9866)
                .longitude(77.7381)
                .build());

        // 2. Shifts
        Shift morningInbound = shiftRepository.save(Shift.builder()
                .office(bellandurHq)
                .name("Morning Inbound (09:00 - 18:00)")
                .startTime(LocalTime.of(9, 0, 0))
                .endTime(LocalTime.of(18, 0, 0))
                .shiftType(ShiftType.INBOUND)
                .cutoffMinutes(60)
                .build());

        shiftRepository.save(Shift.builder()
                .office(bellandurHq)
                .name("Evening Outbound (18:00 - 03:00)")
                .startTime(LocalTime.of(18, 0, 0))
                .endTime(LocalTime.of(3, 0, 0))
                .shiftType(ShiftType.OUTBOUND)
                .cutoffMinutes(60)
                .build());

        Shift nightOutbound = shiftRepository.save(Shift.builder()
                .office(bellandurHq)
                .name("Night Outbound (21:30 - 06:00)")
                .startTime(LocalTime.of(21, 30, 0))
                .endTime(LocalTime.of(6, 0, 0))
                .shiftType(ShiftType.OUTBOUND)
                .cutoffMinutes(90)
                .build());

        shiftRepository.save(Shift.builder()
                .office(bellandurHq)
                .name("Late Night Inbound (23:00 - 08:00)")
                .startTime(LocalTime.of(23, 0, 0))
                .endTime(LocalTime.of(8, 0, 0))
                .shiftType(ShiftType.INBOUND)
                .cutoffMinutes(90)
                .build());

        // 3. Fleet Cabs
        cabRepository.save(Cab.builder()
                .licensePlate("KA-01-AB-1001")
                .model("Toyota Innova Crysta")
                .capacity(6)
                .driverName("Manjunath Gowda")
                .driverPhone("+91 98450 11223")
                .isActive(true)
                .build());

        cabRepository.save(Cab.builder()
                .licensePlate("KA-03-CD-2002")
                .model("Maruti Suzuki Ertiga")
                .capacity(4)
                .driverName("Suresh Babu")
                .driverPhone("+91 98450 33445")
                .isActive(true)
                .build());

        cabRepository.save(Cab.builder()
                .licensePlate("KA-05-EF-3003")
                .model("Mahindra Marazzo")
                .capacity(4)
                .driverName("Ganesh Hegde")
                .driverPhone("+91 98450 55667")
                .isActive(true)
                .build());

        cabRepository.save(Cab.builder()
                .licensePlate("KA-01-GH-4004")
                .model("Toyota Rumion")
                .capacity(4)
                .driverName("Ravi Shankar")
                .driverPhone("+91 98450 77889")
                .isActive(true)
                .build());

        cabRepository.save(Cab.builder()
                .licensePlate("KA-04-IJ-5005")
                .model("Honda City")
                .capacity(3)
                .driverName("Vijay Bhaskar")
                .driverPhone("+91 98450 99001")
                .isActive(true)
                .build());

        // 4. Employees & Admin Dispatcher
        String defaultPasswordHash = passwordEncoder.encode("password123");

        employeeRepository.save(Employee.builder()
                .name("Chief Dispatcher Rajesh")
                .email("admin@moveinsync.com")
                .passwordHash(defaultPasswordHash)
                .role(Role.ROLE_ADMIN)
                .gender(Gender.MALE)
                .phoneNumber("+91 99001 12233")
                .homeAddress("MG Road Corporate Center, Bengaluru")
                .homeLatitude(12.9716)
                .homeLongitude(77.5946)
                .build());

        Employee pooja = employeeRepository.save(Employee.builder()
                .name("Pooja Sharma")
                .email("pooja.sharma@moveinsync.com")
                .passwordHash(defaultPasswordHash)
                .role(Role.ROLE_EMPLOYEE)
                .gender(Gender.FEMALE)
                .phoneNumber("+91 98765 43201")
                .homeAddress("100ft Road, Indiranagar, Bengaluru")
                .homeLatitude(12.9719)
                .homeLongitude(77.6412)
                .build());

        Employee arun = employeeRepository.save(Employee.builder()
                .name("Arun Kumar")
                .email("arun.kumar@moveinsync.com")
                .passwordHash(defaultPasswordHash)
                .role(Role.ROLE_EMPLOYEE)
                .gender(Gender.MALE)
                .phoneNumber("+91 98765 43202")
                .homeAddress("HSR Layout Sector 1, Bengaluru")
                .homeLatitude(12.9121)
                .homeLongitude(77.6446)
                .build());

        Employee sneha = employeeRepository.save(Employee.builder()
                .name("Sneha Reddy")
                .email("sneha.reddy@moveinsync.com")
                .passwordHash(defaultPasswordHash)
                .role(Role.ROLE_EMPLOYEE)
                .gender(Gender.FEMALE)
                .phoneNumber("+91 98765 43203")
                .homeAddress("Koramangala 4th Block, Bengaluru")
                .homeLatitude(12.9352)
                .homeLongitude(77.6245)
                .build());

        Employee vikas = employeeRepository.save(Employee.builder()
                .name("Vikas Mehta")
                .email("vikas.mehta@moveinsync.com")
                .passwordHash(defaultPasswordHash)
                .role(Role.ROLE_EMPLOYEE)
                .gender(Gender.MALE)
                .phoneNumber("+91 98765 43204")
                .homeAddress("Sarjapur Main Road, Bengaluru")
                .homeLatitude(12.9237)
                .homeLongitude(77.6833)
                .build());

        Employee ananya = employeeRepository.save(Employee.builder()
                .name("Ananya Iyer")
                .email("ananya.iyer@moveinsync.com")
                .passwordHash(defaultPasswordHash)
                .role(Role.ROLE_EMPLOYEE)
                .gender(Gender.FEMALE)
                .phoneNumber("+91 98765 43205")
                .homeAddress("BTM Layout 2nd Stage, Bengaluru")
                .homeLatitude(12.9166)
                .homeLongitude(77.6101)
                .build());

        Employee rohit = employeeRepository.save(Employee.builder()
                .name("Rohit Verma")
                .email("rohit.verma@moveinsync.com")
                .passwordHash(defaultPasswordHash)
                .role(Role.ROLE_EMPLOYEE)
                .gender(Gender.MALE)
                .phoneNumber("+91 98765 43206")
                .homeAddress("Marathahalli Bridge, Bengaluru")
                .homeLatitude(12.9569)
                .homeLongitude(77.7011)
                .build());

        // 5. Pre-Seeded Bookings for Tomorrow
        LocalDate targetDate = LocalDate.now().plusDays(1);

        // Morning Shift Bookings (Inbound)
        bookingRepository.save(Booking.builder()
                .employee(pooja)
                .shift(morningInbound)
                .bookingDate(targetDate)
                .pickupLatitude(pooja.getHomeLatitude())
                .pickupLongitude(pooja.getHomeLongitude())
                .pickupAddress(pooja.getHomeAddress())
                .status(BookingStatus.CONFIRMED)
                .build());

        bookingRepository.save(Booking.builder()
                .employee(arun)
                .shift(morningInbound)
                .bookingDate(targetDate)
                .pickupLatitude(arun.getHomeLatitude())
                .pickupLongitude(arun.getHomeLongitude())
                .pickupAddress(arun.getHomeAddress())
                .status(BookingStatus.CONFIRMED)
                .build());

        bookingRepository.save(Booking.builder()
                .employee(sneha)
                .shift(morningInbound)
                .bookingDate(targetDate)
                .pickupLatitude(sneha.getHomeLatitude())
                .pickupLongitude(sneha.getHomeLongitude())
                .pickupAddress(sneha.getHomeAddress())
                .status(BookingStatus.CONFIRMED)
                .build());

        bookingRepository.save(Booking.builder()
                .employee(vikas)
                .shift(morningInbound)
                .bookingDate(targetDate)
                .pickupLatitude(vikas.getHomeLatitude())
                .pickupLongitude(vikas.getHomeLongitude())
                .pickupAddress(vikas.getHomeAddress())
                .status(BookingStatus.CONFIRMED)
                .build());

        // Night Outbound Shift Bookings (Outbound with female employees to trigger night safety escort!)
        bookingRepository.save(Booking.builder()
                .employee(ananya)
                .shift(nightOutbound)
                .bookingDate(targetDate)
                .pickupLatitude(ananya.getHomeLatitude())
                .pickupLongitude(ananya.getHomeLongitude())
                .pickupAddress(ananya.getHomeAddress())
                .status(BookingStatus.CONFIRMED)
                .build());

        bookingRepository.save(Booking.builder()
                .employee(rohit)
                .shift(nightOutbound)
                .bookingDate(targetDate)
                .pickupLatitude(rohit.getHomeLatitude())
                .pickupLongitude(rohit.getHomeLongitude())
                .pickupAddress(rohit.getHomeAddress())
                .status(BookingStatus.CONFIRMED)
                .build());

        bookingRepository.save(Booking.builder()
                .employee(pooja)
                .shift(nightOutbound)
                .bookingDate(targetDate)
                .pickupLatitude(pooja.getHomeLatitude())
                .pickupLongitude(pooja.getHomeLongitude())
                .pickupAddress(pooja.getHomeAddress())
                .status(BookingStatus.CONFIRMED)
                .build());

        log.info("✅ MoveInSync demo data seeded successfully: 3 Offices, 4 Shifts, 5 Cabs, 7 Users, 7 Bookings for date {}.", targetDate);
    }
}
