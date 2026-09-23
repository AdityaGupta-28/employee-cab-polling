package com.cabpooling.employeecabpooling.security;

import com.cabpooling.employeecabpooling.dto.auth.AuthResponse;
import com.cabpooling.employeecabpooling.dto.auth.LoginRequest;
import com.cabpooling.employeecabpooling.dto.auth.RegisterRequest;
import com.cabpooling.employeecabpooling.model.enums.Gender;
import com.cabpooling.employeecabpooling.model.enums.Role;
import com.cabpooling.employeecabpooling.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    private RegisterRequest buildRegisterRequest(String email) {
        return RegisterRequest.builder()
                .email(email)
                .password("password123")
                .name("Test Employee")
                .gender(Gender.MALE)
                .role(Role.ROLE_EMPLOYEE)
                .phoneNumber("+919876543210")
                .homeAddress("HSR Layout, Bangalore")
                .homeLatitude(12.9121)
                .homeLongitude(77.6446)
                .build();
    }

    // -------------------------------------------------------------------------
    // Test 1: Public endpoint is accessible without any token
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Public endpoint should be accessible without authentication")
    void publicEndpoint_shouldReturn200WithoutToken() throws Exception {
        mockMvc.perform(get("/api/test/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("This endpoint is publicly accessible"));
    }

    // -------------------------------------------------------------------------
    // Test 2: Successful employee registration returns JWT
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("POST /api/auth/register should register employee and return JWT")
    void register_shouldReturnJwtOnSuccess() throws Exception {
        RegisterRequest request = buildRegisterRequest("register.test@moveinsync.com");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.email").value("register.test@moveinsync.com"))
                .andExpect(jsonPath("$.role").value("ROLE_EMPLOYEE"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        AuthResponse response = objectMapper.readValue(body, AuthResponse.class);
        assertThat(response.getToken()).isNotBlank();
    }

    // -------------------------------------------------------------------------
    // Test 3: Duplicate email registration returns 400 Bad Request
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("POST /api/auth/register should reject duplicate email with 400")
    void register_shouldRejectDuplicateEmail() throws Exception {
        RegisterRequest request = buildRegisterRequest("duplicate@moveinsync.com");

        // First registration
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Second registration with the same email
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("already registered")));
    }

    // -------------------------------------------------------------------------
    // Test 4: Successful login returns valid JWT
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("POST /api/auth/login should return JWT on valid credentials")
    void login_shouldReturnJwtOnValidCredentials() throws Exception {
        // Register first
        authService.register(buildRegisterRequest("login.test@moveinsync.com"));

        LoginRequest loginRequest = LoginRequest.builder()
                .email("login.test@moveinsync.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("login.test@moveinsync.com"));
    }

    // -------------------------------------------------------------------------
    // Test 5: Login with wrong password returns 401 Unauthorized
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("POST /api/auth/login should return 401 on wrong password")
    void login_shouldReturn401OnBadPassword() throws Exception {
        authService.register(buildRegisterRequest("badpass.test@moveinsync.com"));

        LoginRequest loginRequest = LoginRequest.builder()
                .email("badpass.test@moveinsync.com")
                .password("wrongpassword")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Test 6: GET /api/auth/me with valid token returns user profile
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("GET /api/auth/me with valid token should return profile")
    void getMe_shouldReturnProfileWithValidToken() throws Exception {
        AuthResponse registered = authService.register(buildRegisterRequest("me.test@moveinsync.com"));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + registered.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me.test@moveinsync.com"))
                .andExpect(jsonPath("$.role").value("ROLE_EMPLOYEE"));
    }

    // -------------------------------------------------------------------------
    // Test 7: GET /api/auth/me without token returns 401
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("GET /api/auth/me without token should return 401")
    void getMe_shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Test 8: Admin-only endpoint forbidden for ROLE_EMPLOYEE
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Admin-only endpoint should return 403 for ROLE_EMPLOYEE")
    void adminEndpoint_shouldReturn403ForEmployee() throws Exception {
        AuthResponse employee = authService.register(buildRegisterRequest("employee.rbac@moveinsync.com"));

        mockMvc.perform(get("/api/test/admin-only")
                        .header("Authorization", "Bearer " + employee.getToken()))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // Test 9: Admin-only endpoint accessible for ROLE_ADMIN
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Admin-only endpoint should return 200 for ROLE_ADMIN")
    void adminEndpoint_shouldReturn200ForAdmin() throws Exception {
        RegisterRequest adminRequest = buildRegisterRequest("admin.rbac@moveinsync.com");
        adminRequest.setRole(Role.ROLE_ADMIN);
        AuthResponse admin = authService.register(adminRequest);

        mockMvc.perform(get("/api/test/admin-only")
                        .header("Authorization", "Bearer " + admin.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("admin.rbac@moveinsync.com")));
    }
}
