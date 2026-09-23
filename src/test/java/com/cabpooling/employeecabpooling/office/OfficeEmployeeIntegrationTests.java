package com.cabpooling.employeecabpooling.office;

import com.cabpooling.employeecabpooling.dto.auth.AuthResponse;
import com.cabpooling.employeecabpooling.dto.auth.RegisterRequest;
import com.cabpooling.employeecabpooling.dto.employee.EmployeeUpdateRequest;
import com.cabpooling.employeecabpooling.dto.office.OfficeRequest;
import com.cabpooling.employeecabpooling.model.enums.Gender;
import com.cabpooling.employeecabpooling.model.enums.Role;
import com.cabpooling.employeecabpooling.service.AuthService;
import com.cabpooling.employeecabpooling.service.OfficeService;
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
class OfficeEmployeeIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @Autowired private OfficeService officeService;

    private String adminToken;
    private String employeeToken;
    private Long employeeId;
    private Long adminId;

    @BeforeEach
    void setUp() {
        AuthResponse admin = authService.register(RegisterRequest.builder()
                .email("admin4@test.com").password("password123").name("Admin User")
                .gender(Gender.MALE).role(Role.ROLE_ADMIN).phoneNumber("+919000000001")
                .homeAddress("MG Road, Bangalore").homeLatitude(12.97).homeLongitude(77.59)
                .build());
        adminToken = "Bearer " + admin.getToken();
        adminId = admin.getId();

        AuthResponse emp = authService.register(RegisterRequest.builder()
                .email("emp4@test.com").password("password123").name("Emp User")
                .gender(Gender.FEMALE).role(Role.ROLE_EMPLOYEE).phoneNumber("+919000000002")
                .homeAddress("HSR Layout, Bangalore").homeLatitude(12.91).homeLongitude(77.64)
                .build());
        employeeToken = "Bearer " + emp.getToken();
        employeeId = emp.getId();
    }

    // =========================================================================
    // OFFICE TESTS
    // =========================================================================

    private OfficeRequest sampleOffice() {
        return OfficeRequest.builder()
                .name("Bangalore HQ")
                .address("Whitefield, Bangalore")
                .latitude(12.9698)
                .longitude(77.7499)
                .build();
    }

    @Test
    @DisplayName("Admin can create an office")
    void createOffice_admin_shouldReturn200() throws Exception {
        mockMvc.perform(post("/api/offices")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleOffice())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Bangalore HQ"));
    }

    @Test
    @DisplayName("Employee cannot create an office")
    void createOffice_employee_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/offices")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleOffice())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Authenticated user can list all offices")
    void listOffices_authenticated_shouldReturn200() throws Exception {
        officeService.create(sampleOffice());

        mockMvc.perform(get("/api/offices")
                        .header("Authorization", employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("Get office by ID returns 200")
    void getOfficeById_shouldReturn200() throws Exception {
        var office = officeService.create(sampleOffice());

        mockMvc.perform(get("/api/offices/" + office.getId())
                        .header("Authorization", employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(office.getId()));
    }

    @Test
    @DisplayName("Get office with unknown ID returns 404")
    void getOfficeById_unknownId_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/offices/999999")
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Admin can update an office")
    void updateOffice_admin_shouldReturn200() throws Exception {
        var office = officeService.create(sampleOffice());

        OfficeRequest updateReq = OfficeRequest.builder()
                .name("Updated HQ").address("Koramangala, Bangalore")
                .latitude(12.93).longitude(77.62).build();

        mockMvc.perform(put("/api/offices/" + office.getId())
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated HQ"));
    }

    @Test
    @DisplayName("Admin can delete an office")
    void deleteOffice_admin_shouldReturn204() throws Exception {
        var office = officeService.create(sampleOffice());

        mockMvc.perform(delete("/api/offices/" + office.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isNoContent());
    }

    // =========================================================================
    // EMPLOYEE TESTS
    // =========================================================================

    @Test
    @DisplayName("Admin can list all employees (paginated)")
    void listEmployees_admin_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/employees?page=0&size=10")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @DisplayName("Employee cannot list all employees")
    void listEmployees_employee_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/employees")
                        .header("Authorization", employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Employee can view their own profile")
    void getEmployee_self_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/employees/" + employeeId)
                        .header("Authorization", employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("emp4@test.com"));
    }

    @Test
    @DisplayName("Employee cannot view another employee's profile")
    void getEmployee_otherProfile_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/employees/" + adminId)
                        .header("Authorization", employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin can view any employee profile")
    void getEmployee_admin_anyProfile_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/employees/" + employeeId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("emp4@test.com"));
    }

    @Test
    @DisplayName("Employee can update their own profile")
    void updateEmployee_self_shouldReturn200() throws Exception {
        EmployeeUpdateRequest updateReq = EmployeeUpdateRequest.builder()
                .name("Updated Name").phoneNumber("+919876500000")
                .homeAddress("Indiranagar, Bangalore")
                .homeLatitude(12.9784).homeLongitude(77.6408).build();

        mockMvc.perform(put("/api/employees/" + employeeId)
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.homeAddress").value("Indiranagar, Bangalore"));
    }

    @Test
    @DisplayName("Employee cannot update another employee's profile")
    void updateEmployee_otherProfile_shouldReturn403() throws Exception {
        EmployeeUpdateRequest updateReq = EmployeeUpdateRequest.builder()
                .name("Hacked Name").phoneNumber("+919876500001")
                .homeAddress("Somewhere").homeLatitude(12.0).homeLongitude(77.0).build();

        mockMvc.perform(put("/api/employees/" + adminId)
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin can delete an employee")
    void deleteEmployee_admin_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/employees/" + employeeId)
                        .header("Authorization", adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Employee cannot delete another employee")
    void deleteEmployee_employee_shouldReturn403() throws Exception {
        mockMvc.perform(delete("/api/employees/" + adminId)
                        .header("Authorization", employeeToken))
                .andExpect(status().isForbidden());
    }
}
