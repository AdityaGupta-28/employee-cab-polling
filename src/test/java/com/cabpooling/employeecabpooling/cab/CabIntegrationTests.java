package com.cabpooling.employeecabpooling.cab;

import com.cabpooling.employeecabpooling.dto.auth.AuthResponse;
import com.cabpooling.employeecabpooling.dto.auth.RegisterRequest;
import com.cabpooling.employeecabpooling.dto.cab.CabRequest;
import com.cabpooling.employeecabpooling.dto.cab.CabStatusRequest;
import com.cabpooling.employeecabpooling.model.enums.Gender;
import com.cabpooling.employeecabpooling.model.enums.Role;
import com.cabpooling.employeecabpooling.service.AuthService;
import com.cabpooling.employeecabpooling.service.CabService;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CabIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @Autowired private CabService cabService;

    private String adminToken;
    private String employeeToken;

    @BeforeEach
    void setUp() {
        AuthResponse admin = authService.register(RegisterRequest.builder()
                .email("admin5@test.com").password("password123").name("Admin Five")
                .gender(Gender.MALE).role(Role.ROLE_ADMIN).phoneNumber("+919100000001")
                .homeAddress("MG Road, Bangalore").homeLatitude(12.97).homeLongitude(77.59)
                .build());
        adminToken = "Bearer " + admin.getToken();

        AuthResponse emp = authService.register(RegisterRequest.builder()
                .email("emp5@test.com").password("password123").name("Emp Five")
                .gender(Gender.FEMALE).role(Role.ROLE_EMPLOYEE).phoneNumber("+919100000002")
                .homeAddress("HSR Layout, Bangalore").homeLatitude(12.91).homeLongitude(77.64)
                .build());
        employeeToken = "Bearer " + emp.getToken();
    }

    private CabRequest sampleCab(String plate) {
        return CabRequest.builder()
                .licensePlate(plate)
                .model("Toyota Innova")
                .capacity(7)
                .driverName("Raju Kumar")
                .driverPhone("+919876543210")
                .build();
    }

    // =========================================================================
    // CREATE
    // =========================================================================

    @Test
    @DisplayName("Admin can register a new cab")
    void createCab_admin_shouldReturn200() throws Exception {
        mockMvc.perform(post("/api/cabs")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleCab("KA01AB1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.licensePlate").value("KA01AB1234"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("Registering duplicate license plate returns 409")
    void createCab_duplicatePlate_shouldReturn409() throws Exception {
        cabService.create(sampleCab("KA01DUP001"));

        mockMvc.perform(post("/api/cabs")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleCab("KA01DUP001"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("Employee cannot register a cab")
    void createCab_employee_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/cabs")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleCab("KA01EM0001"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Invalid capacity returns 400 validation error")
    void createCab_invalidCapacity_shouldReturn400() throws Exception {
        CabRequest bad = CabRequest.builder()
                .licensePlate("KA01BAD001").model("Test").capacity(0)
                .driverName("Driver").driverPhone("+919876543210").build();

        mockMvc.perform(post("/api/cabs")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.capacity").exists());
    }

    // =========================================================================
    // READ
    // =========================================================================

    @Test
    @DisplayName("Authenticated user can list all cabs")
    void listAllCabs_shouldReturn200() throws Exception {
        cabService.create(sampleCab("KA01LST001"));
        cabService.create(sampleCab("KA01LST002"));

        mockMvc.perform(get("/api/cabs")
                        .header("Authorization", employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @DisplayName("?active=true returns only active cabs")
    void listActiveCabs_shouldReturn200() throws Exception {
        var active = cabService.create(sampleCab("KA01ACT001"));
        var inactive = cabService.create(sampleCab("KA01INA001"));
        cabService.updateStatus(inactive.getId(), new CabStatusRequest(false));

        mockMvc.perform(get("/api/cabs?active=true")
                        .header("Authorization", employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].isActive", everyItem(is(true))));
    }

    @Test
    @DisplayName("Get cab by ID returns 200")
    void getCabById_shouldReturn200() throws Exception {
        var cab = cabService.create(sampleCab("KA01GET001"));

        mockMvc.perform(get("/api/cabs/" + cab.getId())
                        .header("Authorization", employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cab.getId()))
                .andExpect(jsonPath("$.licensePlate").value("KA01GET001"));
    }

    @Test
    @DisplayName("Get cab with unknown ID returns 404")
    void getCabById_unknown_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/cabs/999999")
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    @Test
    @DisplayName("Admin can update a cab")
    void updateCab_admin_shouldReturn200() throws Exception {
        var cab = cabService.create(sampleCab("KA01UPD001"));

        CabRequest update = CabRequest.builder()
                .licensePlate("KA01UPD001").model("Maruti Ertiga")
                .capacity(6).driverName("Shyam").driverPhone("+919876500000").build();

        mockMvc.perform(put("/api/cabs/" + cab.getId())
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("Maruti Ertiga"))
                .andExpect(jsonPath("$.capacity").value(6));
    }

    @Test
    @DisplayName("Admin can toggle cab to inactive")
    void patchCabStatus_inactive_shouldReturn200() throws Exception {
        var cab = cabService.create(sampleCab("KA01STS001"));

        mockMvc.perform(patch("/api/cabs/" + cab.getId() + "/status")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CabStatusRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    @DisplayName("Employee cannot toggle cab status")
    void patchCabStatus_employee_shouldReturn403() throws Exception {
        var cab = cabService.create(sampleCab("KA01STS002"));

        mockMvc.perform(patch("/api/cabs/" + cab.getId() + "/status")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CabStatusRequest(false))))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    @Test
    @DisplayName("Admin can delete a cab")
    void deleteCab_admin_shouldReturn204() throws Exception {
        var cab = cabService.create(sampleCab("KA01DEL001"));

        mockMvc.perform(delete("/api/cabs/" + cab.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Employee cannot delete a cab")
    void deleteCab_employee_shouldReturn403() throws Exception {
        var cab = cabService.create(sampleCab("KA01DEL002"));

        mockMvc.perform(delete("/api/cabs/" + cab.getId())
                        .header("Authorization", employeeToken))
                .andExpect(status().isForbidden());
    }
}
