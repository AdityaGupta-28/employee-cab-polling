package com.cabpooling.employeecabpooling.allocation;

import com.cabpooling.employeecabpooling.dto.allocation.AutoClusterRequest;
import com.cabpooling.employeecabpooling.dto.auth.AuthResponse;
import com.cabpooling.employeecabpooling.dto.auth.RegisterRequest;
import com.cabpooling.employeecabpooling.dto.booking.BookingRequest;
import com.cabpooling.employeecabpooling.dto.cab.CabRequest;
import com.cabpooling.employeecabpooling.dto.cab.CabResponse;
import com.cabpooling.employeecabpooling.dto.office.OfficeRequest;
import com.cabpooling.employeecabpooling.dto.office.OfficeResponse;
import com.cabpooling.employeecabpooling.dto.shift.ShiftRequest;
import com.cabpooling.employeecabpooling.dto.shift.ShiftResponse;
import com.cabpooling.employeecabpooling.model.enums.Gender;
import com.cabpooling.employeecabpooling.model.enums.Role;
import com.cabpooling.employeecabpooling.model.enums.ShiftType;
import com.cabpooling.employeecabpooling.routing.DistanceCacheService;
import com.cabpooling.employeecabpooling.routing.DistanceProvider;
import com.cabpooling.employeecabpooling.routing.DistanceResult;
import com.cabpooling.employeecabpooling.service.AuthService;
import com.cabpooling.employeecabpooling.service.CabService;
import com.cabpooling.employeecabpooling.service.OfficeService;
import com.cabpooling.employeecabpooling.service.ShiftService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AllocationRouteOptimizationIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @Autowired private OfficeService officeService;
    @Autowired private ShiftService shiftService;
    @Autowired private CabService cabService;
    @Autowired private DistanceCacheService distanceCacheService;

    @Autowired
    @Qualifier("haversineDistanceProvider")
    private DistanceProvider haversineProvider;

    @Autowired
    @Qualifier("osrmDistanceProvider")
    private DistanceProvider osrmProvider;

    private String adminToken;
    private String employee1Token;
    private String employee2Token;
    private String employeeFemaleToken;

    private Long officeId;
    private Long dayShiftId;
    private Long nightShiftId;
    private LocalDate targetDate;

    @BeforeEach
    void setUp() {
        distanceCacheService.clear();

        AuthResponse admin = authService.register(RegisterRequest.builder()
                .email("admin9@test.com").password("password123").name("Admin Nine")
                .gender(Gender.MALE).role(Role.ROLE_ADMIN).phoneNumber("+919500000001")
                .homeAddress("MG Road, Bangalore").homeLatitude(12.9716).homeLongitude(77.5946).build());
        adminToken = "Bearer " + admin.getToken();

        AuthResponse emp1 = authService.register(RegisterRequest.builder()
                .email("emp9a@test.com").password("password123").name("Rohan")
                .gender(Gender.MALE).role(Role.ROLE_EMPLOYEE).phoneNumber("+919500000002")
                .homeAddress("Koramangala 4th Block").homeLatitude(12.9352).homeLongitude(77.6245).build());
        employee1Token = "Bearer " + emp1.getToken();

        AuthResponse emp2 = authService.register(RegisterRequest.builder()
                .email("emp9b@test.com").password("password123").name("Amit")
                .gender(Gender.MALE).role(Role.ROLE_EMPLOYEE).phoneNumber("+919500000003")
                .homeAddress("HSR Layout Sector 1").homeLatitude(12.9121).homeLongitude(77.6446).build());
        employee2Token = "Bearer " + emp2.getToken();

        AuthResponse empFemale = authService.register(RegisterRequest.builder()
                .email("emp9c@test.com").password("password123").name("Pooja")
                .gender(Gender.FEMALE).role(Role.ROLE_EMPLOYEE).phoneNumber("+919500000004")
                .homeAddress("Bellandur Outer Ring Rd").homeLatitude(12.9260).homeLongitude(77.6762).build());
        employeeFemaleToken = "Bearer " + empFemale.getToken();

        OfficeResponse office = officeService.create(OfficeRequest.builder()
                .name("Global Tech Hub").address("Outer Ring Road, Bangalore")
                .latitude(12.9850).longitude(77.7300).build());
        officeId = office.getId();

        // Day Shift (9 AM - 6 PM)
        ShiftResponse dayShift = shiftService.create(ShiftRequest.builder()
                .officeId(officeId).name("Standard Day Shift")
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0))
                .shiftType(ShiftType.INBOUND).cutoffMinutes(0).build());
        dayShiftId = dayShift.getId();

        // Night Shift (10 PM - 6 AM)
        ShiftResponse nightShift = shiftService.create(ShiftRequest.builder()
                .officeId(officeId).name("Night Outbound Shift")
                .startTime(LocalTime.of(22, 0)).endTime(LocalTime.of(6, 0))
                .shiftType(ShiftType.OUTBOUND).cutoffMinutes(0).build());
        nightShiftId = nightShift.getId();

        // Fleet Cabs
        cabService.create(CabRequest.builder()
                .licensePlate("KA01AL9001").model("Toyota Innova").capacity(4)
                .driverName("Ramesh").driverPhone("+919876543201").build());

        cabService.create(CabRequest.builder()
                .licensePlate("KA01AL9002").model("Maruti Ertiga").capacity(4)
                .driverName("Suresh").driverPhone("+919876543202").build());

        targetDate = LocalDate.now().plusDays(3);
    }

    // =========================================================================
    // DISTANCE PROVIDER & STRATEGY PATTERN TESTS
    // =========================================================================

    @Test
    @DisplayName("Haversine provider calculates distance, duration and caches results")
    void haversineProvider_shouldComputeAndCache() {
        double lat1 = 12.9716, lon1 = 77.5946; // MG Road
        double lat2 = 12.9352, lon2 = 77.6245; // Koramangala

        DistanceResult r1 = haversineProvider.calculateDistanceAndDuration(lat1, lon1, lat2, lon2);
        assertTrue(r1.getDistanceKm() > 0.0);
        assertTrue(r1.getDurationMinutes() > 0);
        assertEquals(1, distanceCacheService.size());

        // Second call should fetch from cache
        DistanceResult r2 = haversineProvider.calculateDistanceAndDuration(lat1, lon1, lat2, lon2);
        assertEquals(r1.getDistanceKm(), r2.getDistanceKm());
    }

    @Test
    @DisplayName("OSRM provider falls back seamlessly to Haversine on lookup")
    void osrmProvider_shouldCalculateDistance() {
        double lat1 = 12.9716, lon1 = 77.5946;
        double lat2 = 12.9352, lon2 = 77.6245;

        DistanceResult r = osrmProvider.calculateDistanceAndDuration(lat1, lon1, lat2, lon2);
        assertTrue(r.getDistanceKm() > 0.0);
        assertTrue(r.getDurationMinutes() > 0);
    }

    // =========================================================================
    // AUTO-CLUSTERING TESTS
    // =========================================================================

    @Test
    @DisplayName("Admin can trigger auto-clustering to allocate cabs and build optimized routes")
    void autoCluster_admin_shouldCreateAssignmentsWithOptimizedRoutes() throws Exception {
        // Bookings for day shift
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employee1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingRequest.builder()
                                .shiftId(dayShiftId).bookingDate(targetDate)
                                .pickupLatitude(12.9352).pickupLongitude(77.6245)
                                .pickupAddress("Koramangala").build())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employee2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingRequest.builder()
                                .shiftId(dayShiftId).bookingDate(targetDate)
                                .pickupLatitude(12.9121).pickupLongitude(77.6446)
                                .pickupAddress("HSR Layout").build())))
                .andExpect(status().isOk());

        AutoClusterRequest req = AutoClusterRequest.builder()
                .shiftId(dayShiftId)
                .assignmentDate(targetDate)
                .build();

        mockMvc.perform(post("/api/allocations/auto-cluster")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shiftId").value(dayShiftId))
                .andExpect(jsonPath("$.totalBookingsClustered").value(2))
                .andExpect(jsonPath("$.totalCabsAssigned").value(1))
                .andExpect(jsonPath("$.assignments", hasSize(1)))
                .andExpect(jsonPath("$.assignments[0].stops", hasSize(3))) // 2 pickups + 1 office
                .andExpect(jsonPath("$.assignments[0].totalDistanceKm", greaterThan(0.0)));
    }

    @Test
    @DisplayName("Employee cannot trigger auto-clustering")
    void autoCluster_employee_shouldReturn403() throws Exception {
        AutoClusterRequest req = AutoClusterRequest.builder()
                .shiftId(dayShiftId)
                .assignmentDate(targetDate)
                .build();

        mockMvc.perform(post("/api/allocations/auto-cluster")
                        .header("Authorization", employee1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // NIGHT SAFETY GUARD TESTS
    // =========================================================================

    @Test
    @DisplayName("Auto-clustering on night shift with female passenger automatically attaches escort")
    void autoCluster_nightShift_femalePassenger_shouldAttachEscort() throws Exception {
        // Female employee books night outbound shift
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employeeFemaleToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingRequest.builder()
                                .shiftId(nightShiftId).bookingDate(targetDate)
                                .pickupLatitude(12.9260).pickupLongitude(77.6762)
                                .pickupAddress("Bellandur").build())))
                .andExpect(status().isOk());

        AutoClusterRequest req = AutoClusterRequest.builder()
                .shiftId(nightShiftId)
                .assignmentDate(targetDate)
                .build();

        mockMvc.perform(post("/api/allocations/auto-cluster")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments[0].hasEscort").value(true));
    }

    // =========================================================================
    // 2-OPT RE-OPTIMIZATION TEST
    // =========================================================================

    @Test
    @DisplayName("Admin can re-optimize an existing assignment")
    void optimizeAssignment_admin_shouldReturn200() throws Exception {
        // Bookings for day shift
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employee1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingRequest.builder()
                                .shiftId(dayShiftId).bookingDate(targetDate)
                                .pickupLatitude(12.9352).pickupLongitude(77.6245)
                                .pickupAddress("Koramangala").build())))
                .andExpect(status().isOk());

        AutoClusterRequest req = AutoClusterRequest.builder()
                .shiftId(dayShiftId)
                .assignmentDate(targetDate)
                .build();

        String response = mockMvc.perform(post("/api/allocations/auto-cluster")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long assignmentId = objectMapper.readTree(response).path("assignments").get(0).path("id").asLong();

        mockMvc.perform(post("/api/allocations/optimize/" + assignmentId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assignmentId))
                .andExpect(jsonPath("$.stops", hasSize(2))); // 1 pickup + 1 office
    }
}
