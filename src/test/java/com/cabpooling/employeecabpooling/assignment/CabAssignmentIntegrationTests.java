package com.cabpooling.employeecabpooling.assignment;

import com.cabpooling.employeecabpooling.dto.assignment.AssignmentStatusRequest;
import com.cabpooling.employeecabpooling.dto.assignment.CabAssignmentRequest;
import com.cabpooling.employeecabpooling.dto.assignment.CabAssignmentResponse;
import com.cabpooling.employeecabpooling.dto.auth.AuthResponse;
import com.cabpooling.employeecabpooling.dto.auth.RegisterRequest;
import com.cabpooling.employeecabpooling.dto.booking.BookingRequest;
import com.cabpooling.employeecabpooling.dto.cab.CabRequest;
import com.cabpooling.employeecabpooling.dto.cab.CabResponse;
import com.cabpooling.employeecabpooling.dto.office.OfficeRequest;
import com.cabpooling.employeecabpooling.dto.office.OfficeResponse;
import com.cabpooling.employeecabpooling.dto.shift.ShiftRequest;
import com.cabpooling.employeecabpooling.dto.shift.ShiftResponse;
import com.cabpooling.employeecabpooling.model.enums.AssignmentStatus;
import com.cabpooling.employeecabpooling.model.enums.Gender;
import com.cabpooling.employeecabpooling.model.enums.Role;
import com.cabpooling.employeecabpooling.model.enums.ShiftType;
import com.cabpooling.employeecabpooling.model.enums.StopType;
import com.cabpooling.employeecabpooling.service.*;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CabAssignmentIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @Autowired private OfficeService officeService;
    @Autowired private ShiftService shiftService;
    @Autowired private CabService cabService;
    @Autowired private BookingService bookingService;
    @Autowired private CabAssignmentService cabAssignmentService;

    private String adminToken;
    private String employeeToken;
    private String employee2Token;

    private Long officeId;
    private Long inboundShiftId;
    private Long outboundShiftId;
    private Long activeCabId;
    private Long inactiveCabId;
    private LocalDate targetDate;

    @BeforeEach
    void setUp() {
        AuthResponse admin = authService.register(RegisterRequest.builder()
                .email("admin7@test.com").password("password123").name("Admin Seven")
                .gender(Gender.MALE).role(Role.ROLE_ADMIN).phoneNumber("+919300000001")
                .homeAddress("MG Road, Bangalore").homeLatitude(12.9716).homeLongitude(77.5946).build());
        adminToken = "Bearer " + admin.getToken();

        AuthResponse emp1 = authService.register(RegisterRequest.builder()
                .email("emp7a@test.com").password("password123").name("Emp Seven A")
                .gender(Gender.FEMALE).role(Role.ROLE_EMPLOYEE).phoneNumber("+919300000002")
                .homeAddress("Koramangala, Bangalore").homeLatitude(12.9352).homeLongitude(77.6245).build());
        employeeToken = "Bearer " + emp1.getToken();

        AuthResponse emp2 = authService.register(RegisterRequest.builder()
                .email("emp7b@test.com").password("password123").name("Emp Seven B")
                .gender(Gender.MALE).role(Role.ROLE_EMPLOYEE).phoneNumber("+919300000003")
                .homeAddress("HSR Layout, Bangalore").homeLatitude(12.9121).homeLongitude(77.6446).build());
        employee2Token = "Bearer " + emp2.getToken();

        // Create Office in Whitefield
        OfficeResponse office = officeService.create(OfficeRequest.builder()
                .name("Tech Park Hub").address("ITPL Main Rd, Whitefield, Bangalore")
                .latitude(12.9850).longitude(77.7300).build());
        officeId = office.getId();

        // Inbound & Outbound shifts
        ShiftResponse inShift = shiftService.create(ShiftRequest.builder()
                .officeId(officeId).name("Morning Inbound")
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0))
                .shiftType(ShiftType.INBOUND).cutoffMinutes(0).build());
        inboundShiftId = inShift.getId();

        ShiftResponse outShift = shiftService.create(ShiftRequest.builder()
                .officeId(officeId).name("Evening Outbound")
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(23, 0))
                .shiftType(ShiftType.OUTBOUND).cutoffMinutes(0).build());
        outboundShiftId = outShift.getId();

        // Active Cab
        CabResponse activeCab = cabService.create(CabRequest.builder()
                .licensePlate("KA03MN7001").model("Toyota Innova").capacity(4)
                .driverName("Suresh").driverPhone("+919876543211").build());
        activeCabId = activeCab.getId();

        // Inactive Cab
        CabResponse inactiveCab = cabService.create(CabRequest.builder()
                .licensePlate("KA03MN7002").model("Maruti Ertiga").capacity(4)
                .driverName("Ramesh").driverPhone("+919876543212").build());
        inactiveCabId = inactiveCab.getId();
        cabService.updateStatus(inactiveCabId, com.cabpooling.employeecabpooling.dto.cab.CabStatusRequest.builder().isActive(false).build());

        targetDate = LocalDate.now().plusDays(2);
    }

    // =========================================================================
    // ASSIGNMENT CRUD & ROLE TESTS
    // =========================================================================

    @Test
    @DisplayName("Admin can create a cab assignment")
    void createAssignment_admin_shouldReturn200() throws Exception {
        CabAssignmentRequest request = CabAssignmentRequest.builder()
                .cabId(activeCabId)
                .shiftId(inboundShiftId)
                .assignmentDate(targetDate)
                .build();

        mockMvc.perform(post("/api/assignments")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.cabId").value(activeCabId))
                .andExpect(jsonPath("$.shiftId").value(inboundShiftId))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.cabLicensePlate").value("KA03MN7001"));
    }

    @Test
    @DisplayName("Employee cannot create a cab assignment")
    void createAssignment_employee_shouldReturn403() throws Exception {
        CabAssignmentRequest request = CabAssignmentRequest.builder()
                .cabId(activeCabId)
                .shiftId(inboundShiftId)
                .assignmentDate(targetDate)
                .build();

        mockMvc.perform(post("/api/assignments")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Creating duplicate assignment returns 409 Conflict")
    void createAssignment_duplicate_shouldReturn409() throws Exception {
        CabAssignmentRequest request = CabAssignmentRequest.builder()
                .cabId(activeCabId)
                .shiftId(inboundShiftId)
                .assignmentDate(targetDate)
                .build();

        cabAssignmentService.create(request);

        mockMvc.perform(post("/api/assignments")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("Assigning an inactive cab returns 400 Bad Request")
    void createAssignment_inactiveCab_shouldReturn400() throws Exception {
        CabAssignmentRequest request = CabAssignmentRequest.builder()
                .cabId(inactiveCabId)
                .shiftId(inboundShiftId)
                .assignmentDate(targetDate)
                .build();

        mockMvc.perform(post("/api/assignments")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Business Rule Violation"));
    }

    @Test
    @DisplayName("Get assignment by ID returns 200")
    void getAssignmentById_shouldReturn200() throws Exception {
        CabAssignmentResponse created = cabAssignmentService.create(CabAssignmentRequest.builder()
                .cabId(activeCabId).shiftId(inboundShiftId).assignmentDate(targetDate).build());

        mockMvc.perform(get("/api/assignments/" + created.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId()))
                .andExpect(jsonPath("$.status").value("PLANNED"));
    }

    @Test
    @DisplayName("List assignments filtered by date and shiftId returns 200")
    void listAssignments_filtered_shouldReturn200() throws Exception {
        cabAssignmentService.create(CabAssignmentRequest.builder()
                .cabId(activeCabId).shiftId(inboundShiftId).assignmentDate(targetDate).build());

        mockMvc.perform(get("/api/assignments?date=" + targetDate + "&shiftId=" + inboundShiftId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].shiftId").value(inboundShiftId));
    }

    @Test
    @DisplayName("Delete assignment returns 204 No Content")
    void deleteAssignment_shouldReturn204() throws Exception {
        CabAssignmentResponse created = cabAssignmentService.create(CabAssignmentRequest.builder()
                .cabId(activeCabId).shiftId(inboundShiftId).assignmentDate(targetDate).build());

        mockMvc.perform(delete("/api/assignments/" + created.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/assignments/" + created.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // STATUS TRANSITION TESTS
    // =========================================================================

    @Test
    @DisplayName("Status transition: PLANNED -> IN_PROGRESS -> COMPLETED succeeds")
    void updateStatus_validSequence_shouldReturn200() throws Exception {
        CabAssignmentResponse created = cabAssignmentService.create(CabAssignmentRequest.builder()
                .cabId(activeCabId).shiftId(inboundShiftId).assignmentDate(targetDate).build());

        // PLANNED -> IN_PROGRESS
        mockMvc.perform(patch("/api/assignments/" + created.getId() + "/status")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AssignmentStatusRequest.builder().status(AssignmentStatus.IN_PROGRESS).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // IN_PROGRESS -> COMPLETED
        mockMvc.perform(patch("/api/assignments/" + created.getId() + "/status")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AssignmentStatusRequest.builder().status(AssignmentStatus.COMPLETED).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("Invalid status transition returns 400 Bad Request")
    void updateStatus_invalidTransition_shouldReturn400() throws Exception {
        CabAssignmentResponse created = cabAssignmentService.create(CabAssignmentRequest.builder()
                .cabId(activeCabId).shiftId(inboundShiftId).assignmentDate(targetDate).build());

        // PLANNED directly to COMPLETED is invalid
        mockMvc.perform(patch("/api/assignments/" + created.getId() + "/status")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AssignmentStatusRequest.builder().status(AssignmentStatus.COMPLETED).build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Business Rule Violation"));
    }

    // =========================================================================
    // ROUTE PLANNING TESTS
    // =========================================================================

    @Test
    @DisplayName("Build route for INBOUND shift generates ordered pickup stops ending at office")
    void buildRoute_inbound_shouldOrderStopsCorrectly() throws Exception {
        // Create 2 confirmed bookings for inbound shift
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingRequest.builder()
                                .shiftId(inboundShiftId).bookingDate(targetDate)
                                .pickupLatitude(12.9352).pickupLongitude(77.6245)
                                .pickupAddress("Koramangala, Bangalore").build())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employee2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingRequest.builder()
                                .shiftId(inboundShiftId).bookingDate(targetDate)
                                .pickupLatitude(12.9121).pickupLongitude(77.6446)
                                .pickupAddress("HSR Layout, Bangalore").build())))
                .andExpect(status().isOk());

        CabAssignmentResponse assignment = cabAssignmentService.create(CabAssignmentRequest.builder()
                .cabId(activeCabId).shiftId(inboundShiftId).assignmentDate(targetDate).build());

        mockMvc.perform(post("/api/assignments/" + assignment.getId() + "/build-route")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops", hasSize(3))) // 2 Pickups + 1 Office
                .andExpect(jsonPath("$.stops[0].stopType").value("PICKUP"))
                .andExpect(jsonPath("$.stops[0].stopOrder").value(1))
                .andExpect(jsonPath("$.stops[1].stopType").value("PICKUP"))
                .andExpect(jsonPath("$.stops[1].stopOrder").value(2))
                .andExpect(jsonPath("$.stops[2].stopType").value("OFFICE"))
                .andExpect(jsonPath("$.stops[2].stopOrder").value(3))
                .andExpect(jsonPath("$.stops[2].address").value("ITPL Main Rd, Whitefield, Bangalore"))
                .andExpect(jsonPath("$.totalDistanceKm", greaterThan(0.0)))
                .andExpect(jsonPath("$.totalDurationMinutes", greaterThan(0)));
    }

    @Test
    @DisplayName("Build route for OUTBOUND shift starts at office and drops off employees")
    void buildRoute_outbound_shouldStartAtOffice() throws Exception {
        // Create 2 confirmed bookings for outbound shift
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingRequest.builder()
                                .shiftId(outboundShiftId).bookingDate(targetDate)
                                .pickupLatitude(12.9352).pickupLongitude(77.6245)
                                .pickupAddress("Koramangala, Bangalore").build())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employee2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BookingRequest.builder()
                                .shiftId(outboundShiftId).bookingDate(targetDate)
                                .pickupLatitude(12.9121).pickupLongitude(77.6446)
                                .pickupAddress("HSR Layout, Bangalore").build())))
                .andExpect(status().isOk());

        CabAssignmentResponse assignment = cabAssignmentService.create(CabAssignmentRequest.builder()
                .cabId(activeCabId).shiftId(outboundShiftId).assignmentDate(targetDate).build());

        mockMvc.perform(post("/api/assignments/" + assignment.getId() + "/build-route")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops", hasSize(3))) // 1 Office + 2 Dropoffs
                .andExpect(jsonPath("$.stops[0].stopType").value("OFFICE"))
                .andExpect(jsonPath("$.stops[0].stopOrder").value(1))
                .andExpect(jsonPath("$.stops[0].address").value("ITPL Main Rd, Whitefield, Bangalore"))
                .andExpect(jsonPath("$.stops[1].stopType").value("DROPOFF"))
                .andExpect(jsonPath("$.stops[1].stopOrder").value(2))
                .andExpect(jsonPath("$.stops[2].stopType").value("DROPOFF"))
                .andExpect(jsonPath("$.stops[2].stopOrder").value(3))
                .andExpect(jsonPath("$.totalDistanceKm", greaterThan(0.0)))
                .andExpect(jsonPath("$.totalDurationMinutes", greaterThan(0)));
    }

    @Test
    @DisplayName("Build route with 0 confirmed bookings returns empty stops list")
    void buildRoute_emptyBookings_shouldReturnEmptyStops() throws Exception {
        CabAssignmentResponse assignment = cabAssignmentService.create(CabAssignmentRequest.builder()
                .cabId(activeCabId).shiftId(inboundShiftId).assignmentDate(targetDate).build());

        mockMvc.perform(post("/api/assignments/" + assignment.getId() + "/build-route")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops", hasSize(0)))
                .andExpect(jsonPath("$.totalDistanceKm").value(0.0))
                .andExpect(jsonPath("$.totalDurationMinutes").value(0));
    }
}
