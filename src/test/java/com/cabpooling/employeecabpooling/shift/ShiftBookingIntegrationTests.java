package com.cabpooling.employeecabpooling.shift;

import com.cabpooling.employeecabpooling.dto.auth.AuthResponse;
import com.cabpooling.employeecabpooling.dto.auth.RegisterRequest;
import com.cabpooling.employeecabpooling.dto.booking.BookingRequest;
import com.cabpooling.employeecabpooling.dto.shift.ShiftRequest;
import com.cabpooling.employeecabpooling.dto.shift.ShiftResponse;
import com.cabpooling.employeecabpooling.model.enums.Gender;
import com.cabpooling.employeecabpooling.model.enums.Role;
import com.cabpooling.employeecabpooling.model.enums.ShiftType;
import com.cabpooling.employeecabpooling.service.AuthService;
import com.cabpooling.employeecabpooling.service.OfficeService;
import com.cabpooling.employeecabpooling.service.ShiftService;
import com.cabpooling.employeecabpooling.dto.office.OfficeRequest;
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
class ShiftBookingIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @Autowired private OfficeService officeService;
    @Autowired private ShiftService shiftService;

    private String adminToken;
    private String employeeToken;
    private String employee2Token;
    private Long officeId;
    private Long shiftId;

    @BeforeEach
    void setUp() {
        AuthResponse admin = authService.register(RegisterRequest.builder()
                .email("admin6@test.com").password("password123").name("Admin Six")
                .gender(Gender.MALE).role(Role.ROLE_ADMIN).phoneNumber("+919200000001")
                .homeAddress("MG Road, Bangalore").homeLatitude(12.97).homeLongitude(77.59).build());
        adminToken = "Bearer " + admin.getToken();

        AuthResponse emp = authService.register(RegisterRequest.builder()
                .email("emp6a@test.com").password("password123").name("Emp Six A")
                .gender(Gender.FEMALE).role(Role.ROLE_EMPLOYEE).phoneNumber("+919200000002")
                .homeAddress("HSR Layout, Bangalore").homeLatitude(12.91).homeLongitude(77.64).build());
        employeeToken = "Bearer " + emp.getToken();

        AuthResponse emp2 = authService.register(RegisterRequest.builder()
                .email("emp6b@test.com").password("password123").name("Emp Six B")
                .gender(Gender.MALE).role(Role.ROLE_EMPLOYEE).phoneNumber("+919200000003")
                .homeAddress("Koramangala, Bangalore").homeLatitude(12.93).homeLongitude(77.62).build());
        employee2Token = "Bearer " + emp2.getToken();

        // Create a reusable office and shift (cutoffMinutes = 0 → window never closes)
        var office = officeService.create(OfficeRequest.builder()
                .name("Test Office Six").address("Whitefield, Bangalore")
                .latitude(12.97).longitude(77.75).build());
        officeId = office.getId();

        ShiftResponse shift = shiftService.create(ShiftRequest.builder()
                .officeId(officeId).name("Morning Shift")
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0))
                .shiftType(ShiftType.INBOUND).cutoffMinutes(0).build());
        shiftId = shift.getId();
    }

    // =========================================================================
    // SHIFT TESTS
    // =========================================================================

    @Test
    @DisplayName("Admin can create a shift")
    void createShift_admin_shouldReturn200() throws Exception {
        ShiftRequest request = ShiftRequest.builder()
                .officeId(officeId).name("Evening Shift")
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(23, 0))
                .shiftType(ShiftType.OUTBOUND).cutoffMinutes(60).build();

        mockMvc.perform(post("/api/shifts")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.officeName").value("Test Office Six"))
                .andExpect(jsonPath("$.shiftType").value("OUTBOUND"));
    }

    @Test
    @DisplayName("Employee cannot create a shift")
    void createShift_employee_shouldReturn403() throws Exception {
        ShiftRequest request = ShiftRequest.builder()
                .officeId(officeId).name("Attempt Shift")
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0))
                .shiftType(ShiftType.INBOUND).cutoffMinutes(0).build();

        mockMvc.perform(post("/api/shifts")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("List all shifts returns at least the seeded shift")
    void listShifts_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/shifts")
                        .header("Authorization", employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("Filter shifts by officeId returns only that office's shifts")
    void listShiftsByOffice_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/shifts?officeId=" + officeId)
                        .header("Authorization", employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].officeId", everyItem(is(officeId.intValue()))));
    }

    @Test
    @DisplayName("Get shift by ID returns 200")
    void getShiftById_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/shifts/" + shiftId)
                        .header("Authorization", employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(shiftId));
    }

    @Test
    @DisplayName("Update shift (admin) returns 200")
    void updateShift_admin_shouldReturn200() throws Exception {
        ShiftRequest update = ShiftRequest.builder()
                .officeId(officeId).name("Updated Shift")
                .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(19, 0))
                .shiftType(ShiftType.INBOUND).cutoffMinutes(30).build();

        mockMvc.perform(put("/api/shifts/" + shiftId)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Shift"))
                .andExpect(jsonPath("$.cutoffMinutes").value(30));
    }

    @Test
    @DisplayName("Delete shift (admin) returns 204")
    void deleteShift_admin_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/shifts/" + shiftId)
                        .header("Authorization", adminToken))
                .andExpect(status().isNoContent());
    }

    // =========================================================================
    // BOOKING TESTS
    // =========================================================================

    private BookingRequest sampleBooking() {
        return BookingRequest.builder()
                .shiftId(shiftId)
                .bookingDate(LocalDate.now().plusDays(1))
                .pickupLatitude(12.91)
                .pickupLongitude(77.64)
                .pickupAddress("HSR Layout, Bangalore")
                .build();
    }

    @Test
    @DisplayName("Employee can book a cab for a shift")
    void createBooking_employee_shouldReturn200() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleBooking())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.shiftId").value(shiftId));
    }

    @Test
    @DisplayName("Duplicate booking for same shift+date returns 409")
    void createBooking_duplicate_shouldReturn409() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleBooking())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleBooking())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("Booking past cutoff window returns 400")
    void createBooking_pastCutoff_shouldReturn400() throws Exception {
        // Create a shift with cutoffMinutes = 999999 (far future deadline → any booking fails)
        ShiftResponse tightShift = shiftService.create(ShiftRequest.builder()
                .officeId(officeId).name("Tight Shift")
                .startTime(LocalTime.of(0, 1)).endTime(LocalTime.of(1, 0))
                .shiftType(ShiftType.INBOUND).cutoffMinutes(999999).build());

        BookingRequest req = BookingRequest.builder()
                .shiftId(tightShift.getId())
                .bookingDate(LocalDate.now().plusDays(1))
                .pickupLatitude(12.91).pickupLongitude(77.64)
                .pickupAddress("HSR Layout, Bangalore").build();

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Business Rule Violation"));
    }

    @Test
    @DisplayName("Employee can view their own bookings")
    void getMyBookings_shouldReturn200() throws Exception {
        // Create a booking first
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleBooking())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bookings/my")
                        .header("Authorization", employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("Employee can cancel their own booking")
    void cancelBooking_self_shouldReturn200() throws Exception {
        String body = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleBooking())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long bookingId = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(delete("/api/bookings/" + bookingId)
                        .header("Authorization", employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("Employee cannot cancel another employee's booking")
    void cancelBooking_otherEmployee_shouldReturn403() throws Exception {
        String body = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleBooking())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long bookingId = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(delete("/api/bookings/" + bookingId)
                        .header("Authorization", employee2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cancelling an already-cancelled booking returns 400")
    void cancelBooking_alreadyCancelled_shouldReturn400() throws Exception {
        String body = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleBooking())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long bookingId = objectMapper.readTree(body).get("id").asLong();

        // First cancel
        mockMvc.perform(delete("/api/bookings/" + bookingId)
                        .header("Authorization", employeeToken))
                .andExpect(status().isOk());

        // Second cancel — should fail
        mockMvc.perform(delete("/api/bookings/" + bookingId)
                        .header("Authorization", employeeToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Business Rule Violation"));
    }

    @Test
    @DisplayName("Admin can view all bookings")
    void getAllBookings_admin_shouldReturn200() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleBooking())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bookings?page=0&size=10")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("Employee cannot access admin booking list")
    void getAllBookings_employee_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/bookings")
                        .header("Authorization", employeeToken))
                .andExpect(status().isForbidden());
    }
}
