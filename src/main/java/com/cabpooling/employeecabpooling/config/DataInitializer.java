package com.cabpooling.employeecabpooling.config;

import com.cabpooling.employeecabpooling.model.entity.Booking;
import com.cabpooling.employeecabpooling.model.entity.Employee;
import com.cabpooling.employeecabpooling.model.entity.Office;
import com.cabpooling.employeecabpooling.model.entity.Shift;
import com.cabpooling.employeecabpooling.model.enums.BookingStatus;
import com.cabpooling.employeecabpooling.model.enums.Gender;
import com.cabpooling.employeecabpooling.model.enums.Role;
import com.cabpooling.employeecabpooling.model.enums.ShiftType;
import com.cabpooling.employeecabpooling.repository.BookingRepository;
import com.cabpooling.employeecabpooling.repository.CabRepository;
import com.cabpooling.employeecabpooling.repository.EmployeeRepository;
import com.cabpooling.employeecabpooling.repository.OfficeRepository;
import com.cabpooling.employeecabpooling.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final String SEED_PASSWORD = "password123";

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
            log.info("Seed data already present. Repairing credentials, phones, fleet capacity, and bookings.");
            repairSeedAccounts();
            return;
        }

        log.info("Seeding MoveInSync Bangalore offices, fleet, employees, and bookings...");

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

        saveCab("KA-01-AB-1001", "Toyota Innova Crysta", 6, "Manjunath Gowda", "+919845011223");
        saveCab("KA-03-CD-2002", "Maruti Suzuki Ertiga", 4, "Suresh Babu", "+919845033445");
        saveCab("KA-05-EF-3003", "Mahindra Marazzo", 4, "Ganesh Hegde", "+919845055667");
        saveCab("KA-01-GH-4004", "Toyota Rumion", 4, "Ravi Shankar", "+919845077889");
        saveCab("KA-04-IJ-5005", "Honda Amaze", 4, "Vijay Bhaskar", "+919845099001");

        String hash = passwordEncoder.encode(SEED_PASSWORD);

        saveEmployee("Chief Dispatcher Rajesh", "admin@moveinsync.com", hash, Role.ROLE_ADMIN, Gender.MALE,
                "+919900112233", "MG Road Corporate Center, Bengaluru", 12.9716, 77.5946);
        Employee pooja = saveEmployee("Pooja Sharma", "pooja.sharma@moveinsync.com", hash, Role.ROLE_EMPLOYEE, Gender.FEMALE,
                "+919876543201", "100ft Road, Indiranagar, Bengaluru", 12.9719, 77.6412);
        Employee arun = saveEmployee("Arun Kumar", "arun.kumar@moveinsync.com", hash, Role.ROLE_EMPLOYEE, Gender.MALE,
                "+919876543202", "HSR Layout Sector 1, Bengaluru", 12.9121, 77.6446);
        Employee sneha = saveEmployee("Sneha Reddy", "sneha.reddy@moveinsync.com", hash, Role.ROLE_EMPLOYEE, Gender.FEMALE,
                "+919876543203", "Koramangala 4th Block, Bengaluru", 12.9352, 77.6245);
        Employee vikas = saveEmployee("Vikas Mehta", "vikas.mehta@moveinsync.com", hash, Role.ROLE_EMPLOYEE, Gender.MALE,
                "+919876543204", "Sarjapur Main Road, Bengaluru", 12.9237, 77.6833);
        Employee ananya = saveEmployee("Ananya Iyer", "ananya.iyer@moveinsync.com", hash, Role.ROLE_EMPLOYEE, Gender.FEMALE,
                "+919876543205", "BTM Layout 2nd Stage, Bengaluru", 12.9166, 77.6101);
        Employee rohit = saveEmployee("Rohit Verma", "rohit.verma@moveinsync.com", hash, Role.ROLE_EMPLOYEE, Gender.MALE,
                "+919876543206", "Marathahalli Bridge, Bengaluru", 12.9569, 77.7011);

        seedBookingsForDate(LocalDate.now(), morningInbound, nightOutbound, pooja, arun, sneha, vikas, ananya, rohit);
        seedBookingsForDate(LocalDate.now().plusDays(1), morningInbound, nightOutbound, pooja, arun, sneha, vikas, ananya, rohit);

        log.info("Seed complete: 3 offices, 4 shifts, 5 cabs (capacity 4 or 6), 7 users, bookings for today and tomorrow.");
    }

    private void repairSeedAccounts() {
        String hash = passwordEncoder.encode(SEED_PASSWORD);

        repairEmployee("admin@moveinsync.com", "Chief Dispatcher Rajesh", Role.ROLE_ADMIN, Gender.MALE,
                "+919900112233", "MG Road Corporate Center, Bengaluru", 12.9716, 77.5946, hash);
        Employee pooja = repairEmployee("pooja.sharma@moveinsync.com", "Pooja Sharma", Role.ROLE_EMPLOYEE, Gender.FEMALE,
                "+919876543201", "100ft Road, Indiranagar, Bengaluru", 12.9719, 77.6412, hash);
        Employee arun = repairEmployee("arun.kumar@moveinsync.com", "Arun Kumar", Role.ROLE_EMPLOYEE, Gender.MALE,
                "+919876543202", "HSR Layout Sector 1, Bengaluru", 12.9121, 77.6446, hash);
        Employee sneha = repairEmployee("sneha.reddy@moveinsync.com", "Sneha Reddy", Role.ROLE_EMPLOYEE, Gender.FEMALE,
                "+919876543203", "Koramangala 4th Block, Bengaluru", 12.9352, 77.6245, hash);
        Employee vikas = repairEmployee("vikas.mehta@moveinsync.com", "Vikas Mehta", Role.ROLE_EMPLOYEE, Gender.MALE,
                "+919876543204", "Sarjapur Main Road, Bengaluru", 12.9237, 77.6833, hash);
        Employee ananya = repairEmployee("ananya.iyer@moveinsync.com", "Ananya Iyer", Role.ROLE_EMPLOYEE, Gender.FEMALE,
                "+919876543205", "BTM Layout 2nd Stage, Bengaluru", 12.9166, 77.6101, hash);
        Employee rohit = repairEmployee("rohit.verma@moveinsync.com", "Rohit Verma", Role.ROLE_EMPLOYEE, Gender.MALE,
                "+919876543206", "Marathahalli Bridge, Bengaluru", 12.9569, 77.7011, hash);

        cabRepository.findAll().forEach(cab -> {
            if (cab.getCapacity() != 4 && cab.getCapacity() != 6) {
                cab.setCapacity(4);
                cabRepository.save(cab);
            }
            cab.setDriverPhone(normalizePhone(cab.getDriverPhone(), cab.getDriverPhone()));
            cabRepository.save(cab);
        });

        List<Shift> shifts = shiftRepository.findAll();
        Shift morningInbound = shifts.stream()
                .filter(s -> s.getShiftType() == ShiftType.INBOUND && s.getStartTime().equals(LocalTime.of(9, 0)))
                .findFirst()
                .orElse(shifts.stream().filter(s -> s.getShiftType() == ShiftType.INBOUND).findFirst().orElse(null));
        Shift nightOutbound = shifts.stream()
                .filter(s -> s.getShiftType() == ShiftType.OUTBOUND && s.getStartTime().equals(LocalTime.of(21, 30)))
                .findFirst()
                .orElse(shifts.stream().filter(s -> s.getShiftType() == ShiftType.OUTBOUND).findFirst().orElse(null));

        if (morningInbound != null && nightOutbound != null
                && pooja != null && arun != null && sneha != null && vikas != null && ananya != null && rohit != null) {
            seedBookingsForDate(LocalDate.now(), morningInbound, nightOutbound, pooja, arun, sneha, vikas, ananya, rohit);
            seedBookingsForDate(LocalDate.now().plusDays(1), morningInbound, nightOutbound, pooja, arun, sneha, vikas, ananya, rohit);
        }
    }

    private void seedBookingsForDate(LocalDate date, Shift morningInbound, Shift nightOutbound,
                                     Employee pooja, Employee arun, Employee sneha, Employee vikas,
                                     Employee ananya, Employee rohit) {
        ensureConfirmedBooking(pooja, morningInbound, date);
        ensureConfirmedBooking(arun, morningInbound, date);
        ensureConfirmedBooking(sneha, morningInbound, date);
        ensureConfirmedBooking(vikas, morningInbound, date);
        ensureConfirmedBooking(ananya, nightOutbound, date);
        ensureConfirmedBooking(rohit, nightOutbound, date);
        ensureConfirmedBooking(pooja, nightOutbound, date);
    }

    private void ensureConfirmedBooking(Employee employee, Shift shift, LocalDate date) {
        if (bookingRepository.existsByEmployeeIdAndShiftIdAndBookingDateAndStatus(
                employee.getId(), shift.getId(), date, BookingStatus.CONFIRMED)) {
            return;
        }
        bookingRepository.findByEmployeeIdAndShiftIdAndBookingDate(employee.getId(), shift.getId(), date)
                .ifPresentOrElse(existing -> {
                    existing.setStatus(BookingStatus.CONFIRMED);
                    existing.setPickupLatitude(employee.getHomeLatitude());
                    existing.setPickupLongitude(employee.getHomeLongitude());
                    existing.setPickupAddress(employee.getHomeAddress());
                    bookingRepository.save(existing);
                }, () -> bookingRepository.save(Booking.builder()
                        .employee(employee)
                        .shift(shift)
                        .bookingDate(date)
                        .pickupLatitude(employee.getHomeLatitude())
                        .pickupLongitude(employee.getHomeLongitude())
                        .pickupAddress(employee.getHomeAddress())
                        .status(BookingStatus.CONFIRMED)
                        .build()));
    }

    private Employee repairEmployee(String email, String name, Role role, Gender gender, String phone,
                                    String address, double lat, double lon, String passwordHash) {
        return employeeRepository.findByEmail(email).map(existing -> {
            existing.setName(name);
            existing.setRole(role);
            existing.setGender(gender);
            existing.setPhoneNumber(phone);
            existing.setHomeAddress(address);
            existing.setHomeLatitude(lat);
            existing.setHomeLongitude(lon);
            existing.setPasswordHash(passwordHash);
            return employeeRepository.save(existing);
        }).orElseGet(() -> saveEmployee(name, email, passwordHash, role, gender, phone, address, lat, lon));
    }

    private Employee saveEmployee(String name, String email, String hash, Role role, Gender gender,
                                  String phone, String address, double lat, double lon) {
        return employeeRepository.save(Employee.builder()
                .name(name)
                .email(email)
                .passwordHash(hash)
                .role(role)
                .gender(gender)
                .phoneNumber(phone)
                .homeAddress(address)
                .homeLatitude(lat)
                .homeLongitude(lon)
                .build());
    }

    private void saveCab(String plate, String model, int capacity, String driver, String phone) {
        cabRepository.save(Cab.builder()
                .licensePlate(plate)
                .model(model)
                .capacity(capacity)
                .driverName(driver)
                .driverPhone(phone)
                .isActive(true)
                .build());
    }

    private static String normalizePhone(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String digits = raw.replaceAll("[^0-9+]", "");
        return digits.isBlank() ? fallback : digits;
    }
}
