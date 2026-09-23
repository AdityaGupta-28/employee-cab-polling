package com.cabpooling.employeecabpooling.escort;

import com.cabpooling.employeecabpooling.dto.assignment.CabAssignmentRequest;
import com.cabpooling.employeecabpooling.dto.assignment.CabAssignmentResponse;
import com.cabpooling.employeecabpooling.dto.assignment.EscortRequest;
import com.cabpooling.employeecabpooling.dto.assignment.EscortResponse;
import com.cabpooling.employeecabpooling.dto.assignment.EscortStatusRequest;
import com.cabpooling.employeecabpooling.dto.auth.AuthResponse;
import com.cabpooling.employeecabpooling.dto.auth.RegisterRequest;
import com.cabpooling.employeecabpooling.dto.cab.CabRequest;
import com.cabpooling.employeecabpooling.dto.cab.CabResponse;
import com.cabpooling.employeecabpooling.dto.office.OfficeRequest;
import com.cabpooling.employeecabpooling.dto.office.OfficeResponse;
import com.cabpooling.employeecabpooling.dto.shift.ShiftRequest;
import com.cabpooling.employeecabpooling.dto.shift.ShiftResponse;
import com.cabpooling.employeecabpooling.model.enums.EscortStatus;
import com.cabpooling.employeecabpooling.model.enums.Gender;
import com.cabpooling.employeecabpooling.model.enums.Role;
import com.cabpooling.employeecabpooling.model.enums.ShiftType;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EscortIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @Autowired private OfficeService officeService;
    @Autowired private ShiftService shiftService;
    @Autowired private CabService cabService;
    @Autowired private CabAssignmentService cabAssignmentService;
    @Autowired private EscortService escortService;

    private String adminToken;
    private String employeeToken;
    private Long cabAssignmentId;

    @BeforeEach
    void setUp() {
        AuthResponse admin = authService.register(RegisterRequest.builder()
                .email("admin8@test.com").password("password123").name("Admin Eight")
                .gender(Gender.MALE).role(Role.ROLE_ADMIN).phoneNumber("+919400000001")
                .homeAddress("MG Road, Bangalore").homeLatitude(12.9716).homeLongitude(77.5946).build());
        adminToken = "Bearer " + admin.getToken();

        AuthResponse emp = authService.register(RegisterRequest.builder()
                .email("emp8@test.com").password("password123").name("Emp Eight")
                .gender(Gender.FEMALE).role(Role.ROLE_EMPLOYEE).phoneNumber("+919400000002")
                .homeAddress("Koramangala, Bangalore").homeLatitude(12.9352).homeLongitude(77.6245).build());
        employeeToken = "Bearer " + emp.getToken();

        OfficeResponse office = officeService.create(OfficeRequest.builder()
                .name("Safety Hub Office").address("Electronic City, Bangalore")
                .latitude(12.8450).longitude(77.6600).build());

        ShiftResponse shift = shiftService.create(ShiftRequest.builder()
                .officeId(office.getId()).name("Night Outbound Shift")
                .startTime(LocalTime.of(22, 0)).endTime(LocalTime.of(6, 0))
                .shiftType(ShiftType.OUTBOUND).cutoffMinutes(0).build());

        CabResponse cab = cabService.create(CabRequest.builder()
                .licensePlate("KA05ES8001").model("Force Trax").capacity(6)
                .driverName("Gopal").driverPhone("+919876543220").build());

        CabAssignmentResponse assignment = cabAssignmentService.create(CabAssignmentRequest.builder()
                .cabId(cab.getId()).shiftId(shift.getId()).assignmentDate(LocalDate.now().plusDays(3)).build());
        cabAssignmentId = assignment.getId();
    }

    private EscortRequest sampleEscort() {
        return EscortRequest.builder()
                .cabAssignmentId(cabAssignmentId)
                .escortName("Vikram Singh")
                .escortContact("+919811122233")
                .build();
    }

    @Test
    @DisplayName("Admin can assign an escort to a cab assignment")
    void createEscort_admin_shouldReturn200AndSetHasEscortTrue() throws Exception {
        mockMvc.perform(post("/api/escorts")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleEscort())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.cabAssignmentId").value(cabAssignmentId))
                .andExpect(jsonPath("$.escortName").value("Vikram Singh"))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        // Verify CabAssignment hasEscort is true
        CabAssignmentResponse assignment = cabAssignmentService.findById(cabAssignmentId);
        assertTrue(assignment.getHasEscort());
    }

    @Test
    @DisplayName("Employee cannot assign an escort")
    void createEscort_employee_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/escorts")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleEscort())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Assigning duplicate escort to the same cab assignment returns 409")
    void createEscort_duplicate_shouldReturn409() throws Exception {
        escortService.create(sampleEscort());

        mockMvc.perform(post("/api/escorts")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleEscort())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("Assigning escort to non-existent assignment returns 404")
    void createEscort_notFound_shouldReturn404() throws Exception {
        EscortRequest req = EscortRequest.builder()
                .cabAssignmentId(999999L)
                .escortName("Vikram Singh")
                .escortContact("+919811122233")
                .build();

        mockMvc.perform(post("/api/escorts")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("Get escort by ID returns 200")
    void getEscortById_shouldReturn200() throws Exception {
        EscortResponse created = escortService.create(sampleEscort());

        mockMvc.perform(get("/api/escorts/" + created.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId()))
                .andExpect(jsonPath("$.escortName").value("Vikram Singh"));
    }

    @Test
    @DisplayName("Get escort by cab assignment ID returns 200")
    void getEscortByCabAssignmentId_shouldReturn200() throws Exception {
        EscortResponse created = escortService.create(sampleEscort());

        mockMvc.perform(get("/api/escorts/assignment/" + cabAssignmentId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId()))
                .andExpect(jsonPath("$.cabAssignmentId").value(cabAssignmentId));
    }

    @Test
    @DisplayName("List all escorts returns 200")
    void listEscorts_shouldReturn200() throws Exception {
        escortService.create(sampleEscort());

        mockMvc.perform(get("/api/escorts")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("Update escort status to COMPLETED keeps hasEscort true")
    void updateStatus_completed_shouldKeepHasEscortTrue() throws Exception {
        EscortResponse created = escortService.create(sampleEscort());

        mockMvc.perform(patch("/api/escorts/" + created.getId() + "/status")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                EscortStatusRequest.builder().status(EscortStatus.COMPLETED).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        CabAssignmentResponse assignment = cabAssignmentService.findById(cabAssignmentId);
        assertTrue(assignment.getHasEscort());
    }

    @Test
    @DisplayName("Update escort status to CANCELLED sets hasEscort false")
    void updateStatus_cancelled_shouldSetHasEscortFalse() throws Exception {
        EscortResponse created = escortService.create(sampleEscort());

        mockMvc.perform(patch("/api/escorts/" + created.getId() + "/status")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                EscortStatusRequest.builder().status(EscortStatus.CANCELLED).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        CabAssignmentResponse assignment = cabAssignmentService.findById(cabAssignmentId);
        assertFalse(assignment.getHasEscort());
    }

    @Test
    @DisplayName("Delete escort returns 204 and sets hasEscort false")
    void deleteEscort_shouldReturn204AndSetHasEscortFalse() throws Exception {
        EscortResponse created = escortService.create(sampleEscort());

        mockMvc.perform(delete("/api/escorts/" + created.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/escorts/" + created.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound());

        CabAssignmentResponse assignment = cabAssignmentService.findById(cabAssignmentId);
        assertFalse(assignment.getHasEscort());
    }

    @Test
    @DisplayName("Employee cannot delete an escort")
    void deleteEscort_employee_shouldReturn403() throws Exception {
        EscortResponse created = escortService.create(sampleEscort());

        mockMvc.perform(delete("/api/escorts/" + created.getId())
                        .header("Authorization", employeeToken))
                .andExpect(status().isForbidden());
    }
}
