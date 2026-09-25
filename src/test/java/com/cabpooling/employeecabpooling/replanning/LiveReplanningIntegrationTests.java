package com.cabpooling.employeecabpooling.replanning;

import com.cabpooling.employeecabpooling.dto.allocation.AutoClusterRequest;
import com.cabpooling.employeecabpooling.dto.allocation.AutoClusterResponse;
import com.cabpooling.employeecabpooling.dto.assignment.CabAssignmentResponse;
import com.cabpooling.employeecabpooling.dto.auth.AuthResponse;
import com.cabpooling.employeecabpooling.dto.auth.RegisterRequest;
import com.cabpooling.employeecabpooling.dto.booking.BookingRequest;
import com.cabpooling.employeecabpooling.dto.cab.CabRequest;
import com.cabpooling.employeecabpooling.dto.office.OfficeRequest;
import com.cabpooling.employeecabpooling.dto.office.OfficeResponse;
import com.cabpooling.employeecabpooling.dto.shift.ShiftRequest;
import com.cabpooling.employeecabpooling.dto.shift.ShiftResponse;
import com.cabpooling.employeecabpooling.model.enums.Gender;
import com.cabpooling.employeecabpooling.model.enums.Role;
import com.cabpooling.employeecabpooling.model.enums.ShiftType;
import com.cabpooling.employeecabpooling.service.AllocationService;
import com.cabpooling.employeecabpooling.service.AuthService;
import com.cabpooling.employeecabpooling.service.CabAssignmentService;
import com.cabpooling.employeecabpooling.service.CabService;
import com.cabpooling.employeecabpooling.service.OfficeService;
import com.cabpooling.employeecabpooling.service.ShiftService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
class LiveReplanningIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @Autowired private OfficeService officeService;
    @Autowired private ShiftService shiftService;
    @Autowired private CabService cabService;
    @Autowired private com.cabpooling.employeecabpooling.repository.CabRepository cabRepository;
    @Autowired private AllocationService allocationService;
    @Autowired private CabAssignmentService cabAssignmentService;

    private String adminToken;
    private String employee1Token;
    private String employee2Token;
    private String employee3Token;
    private String employee4Token;
    private String employee5Token;

    private Long officeId;
    private Long shiftId;
    private LocalDate targetDate;

    @BeforeEach
    void setUp() {
        AuthResponse admin = authService.register(RegisterRequest.builder()
                .email("admin10@test.com").password("password123").name("Admin Ten")
                .gender(Gender.MALE).role(Role.ROLE_ADMIN).phoneNumber("+919600000001")
                .homeAddress("MG Road, Bangalore").homeLatitude(12.9716).homeLongitude(77.5946).build());
        adminToken = "Bearer " + admin.getToken();

        AuthResponse emp1 = authService.register(RegisterRequest.builder()
                .email("emp10a@test.com").password("password123").name("Rahul")
                .gender(Gender.MALE).role(Role.ROLE_EMPLOYEE).phoneNumber("+919600000002")
                .homeAddress("Koramangala, Bangalore").homeLatitude(12.9352).homeLongitude(77.6245).build());
        employee1Token = "Bearer " + emp1.getToken();

        AuthResponse emp2 = authService.register(RegisterRequest.builder()
                .email("emp10b@test.com").password("password123").name("Priya")
                .gender(Gender.FEMALE).role(Role.ROLE_EMPLOYEE).phoneNumber("+919600000003")
                .homeAddress("HSR Layout, Bangalore").homeLatitude(12.9121).homeLongitude(77.6446).build());
        employee2Token = "Bearer " + emp2.getToken();

        AuthResponse emp3 = authService.register(RegisterRequest.builder()
                .email("emp10c@test.com").password("password123").name("Kiran")
                .gender(Gender.MALE).role(Role.ROLE_EMPLOYEE).phoneNumber("+919600000004")
                .homeAddress("Bellandur, Bangalore").homeLatitude(12.9260).homeLongitude(77.6762).build());
        employee3Token = "Bearer " + emp3.getToken();

        AuthResponse emp4 = authService.register(RegisterRequest.builder()
                .email("emp10d@test.com").password("password123").name("Neha")
                .gender(Gender.FEMALE).role(Role.ROLE_EMPLOYEE).phoneNumber("+919600000005")
                .homeAddress("BTM Layout, Bangalore").homeLatitude(12.9166).homeLongitude(77.6101).build());
        employee4Token = "Bearer " + emp4.getToken();

        AuthResponse emp5 = authService.register(RegisterRequest.builder()
                .email("emp10e@test.com").password("password123").name("Vikram")
                .gender(Gender.MALE).role(Role.ROLE_EMPLOYEE).phoneNumber("+919600000006")
                .homeAddress("Jayanagar, Bangalore").homeLatitude(12.9308).homeLongitude(77.5838).build());
        employee5Token = "Bearer " + emp5.getToken();

        OfficeResponse office = officeService.create(OfficeRequest.builder()
                .name("Tech Park Main Hub").address("Whitefield, Bangalore")
                .latitude(12.9850).longitude(77.7300).build());
        officeId = office.getId();

        ShiftResponse shift = shiftService.create(ShiftRequest.builder()
                .officeId(officeId).name("Morning Inbound Shift")
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0))
                .shiftType(ShiftType.INBOUND).cutoffMinutes(0).build());
        shiftId = shift.getId();

        cabRepository.findAll().forEach(c -> {
            c.setIsActive(false);
            cabRepository.save(c);
        });

        // Case-study capacity: 4 seats
        cabService.create(CabRequest.builder()
                .licensePlate("KA05RP1001").model("Maruti Ertiga").capacity(4)
                .driverName("Ganesh").driverPhone("+919876543301").build());

        targetDate = LocalDate.now().plusDays(2);
    }

    @Test
    @DisplayName("Cancelling an assigned booking automatically removes stop and recalculates route")
    void cancelBooking_assignedToCab_shouldRerouteCab() throws Exception {
        String b1Res = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employee1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingRequest.builder()
                                .shiftId(shiftId).bookingDate(targetDate)
                                .pickupLatitude(12.9352).pickupLongitude(77.6245)
                                .pickupAddress("Koramangala").build())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long bookingId1 = objectMapper.readTree(b1Res).path("id").asLong();

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employee2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingRequest.builder()
                                .shiftId(shiftId).bookingDate(targetDate)
                                .pickupLatitude(12.9121).pickupLongitude(77.6446)
                                .pickupAddress("HSR Layout").build())))
                .andExpect(status().isOk());

        AutoClusterResponse clusterRes = allocationService.autoCluster(AutoClusterRequest.builder()
                .shiftId(shiftId).assignmentDate(targetDate).build());
        Long assignmentId = clusterRes.getAssignments().get(0).getId();

        CabAssignmentResponse beforeCancel = cabAssignmentService.findById(assignmentId);
        assertEquals(3, beforeCancel.getStops().size());

        mockMvc.perform(delete("/api/bookings/" + bookingId1)
                        .header("Authorization", employee1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        CabAssignmentResponse afterCancel = cabAssignmentService.findById(assignmentId);
        assertEquals(2, afterCancel.getStops().size());
        assertEquals("HSR Layout", afterCancel.getStops().get(0).getAddress());
        assertEquals("Whitefield, Bangalore", afterCancel.getStops().get(1).getAddress());
    }

    @Test
    @DisplayName("Admin can dynamically slot a late booking into an available cab with detour calculation")
    void insertLateBooking_admin_shouldSlotIntoBestCab() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employee1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingRequest.builder()
                                .shiftId(shiftId).bookingDate(targetDate)
                                .pickupLatitude(12.9352).pickupLongitude(77.6245)
                                .pickupAddress("Koramangala").build())))
                .andExpect(status().isOk());

        AutoClusterResponse clusterRes = allocationService.autoCluster(AutoClusterRequest.builder()
                .shiftId(shiftId).assignmentDate(targetDate).build());
        Long assignmentId = clusterRes.getAssignments().get(0).getId();

        String lateBookingRes = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employee3Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingRequest.builder()
                                .shiftId(shiftId).bookingDate(targetDate)
                                .pickupLatitude(12.9260).pickupLongitude(77.6762)
                                .pickupAddress("Bellandur").build())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long lateBookingId = objectMapper.readTree(lateBookingRes).path("id").asLong();

        mockMvc.perform(post("/api/allocations/insert-booking/" + lateBookingId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(lateBookingId))
                .andExpect(jsonPath("$.cabAssignmentId").value(assignmentId))
                .andExpect(jsonPath("$.assignment.stops", hasSize(3)))
                .andExpect(jsonPath("$.detourKm", greaterThanOrEqualTo(0.0)));
    }

    @Test
    @DisplayName("Inserting late booking fails with 400 when all candidate cabs are full")
    void insertLateBooking_fullCapacity_shouldReturn400() throws Exception {
        String[] tokens = {employee1Token, employee2Token, employee3Token, employee4Token};
        double[][] coords = {
                {12.9352, 77.6245}, {12.9121, 77.6446}, {12.9260, 77.6762}, {12.9166, 77.6101}
        };
        String[] addresses = {"Koramangala", "HSR Layout", "Bellandur", "BTM Layout"};

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", tokens[i])
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(BookingRequest.builder()
                                    .shiftId(shiftId).bookingDate(targetDate)
                                    .pickupLatitude(coords[i][0]).pickupLongitude(coords[i][1])
                                    .pickupAddress(addresses[i]).build())))
                    .andExpect(status().isOk());
        }

        allocationService.autoCluster(AutoClusterRequest.builder()
                .shiftId(shiftId).assignmentDate(targetDate).build());

        String lateBookingRes = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employee5Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingRequest.builder()
                                .shiftId(shiftId).bookingDate(targetDate)
                                .pickupLatitude(12.9308).pickupLongitude(77.5838)
                                .pickupAddress("Jayanagar").build())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long lateBookingId = objectMapper.readTree(lateBookingRes).path("id").asLong();

        mockMvc.perform(post("/api/allocations/insert-booking/" + lateBookingId)
                        .header("Authorization", adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Business Rule Violation"));
    }

    @Test
    @DisplayName("Cancel then rebook same shift/date reactivates one seat (idempotent)")
    void cancelThenRebook_shouldReactivateSameSeat() throws Exception {
        String createRes = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employee1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingRequest.builder()
                                .shiftId(shiftId).bookingDate(targetDate)
                                .pickupLatitude(12.9352).pickupLongitude(77.6245)
                                .pickupAddress("Koramangala").build())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long bookingId = objectMapper.readTree(createRes).path("id").asLong();

        mockMvc.perform(delete("/api/bookings/" + bookingId)
                        .header("Authorization", employee1Token))
                .andExpect(status().isOk());

        String rebookRes = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employee1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingRequest.builder()
                                .shiftId(shiftId).bookingDate(targetDate)
                                .pickupLatitude(12.9352).pickupLongitude(77.6245)
                                .pickupAddress("Koramangala Updated").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn().getResponse().getContentAsString();

        assertEquals(bookingId, objectMapper.readTree(rebookRes).path("id").asLong());
    }
}
