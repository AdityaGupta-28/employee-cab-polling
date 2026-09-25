package com.cabpooling.employeecabpooling.dto.cab;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabRequest {

    @NotBlank(message = "License plate is required")
    private String licensePlate;

    @NotBlank(message = "Model is required")
    private String model;

    @NotNull(message = "Capacity is required")
    @Min(value = 4, message = "Capacity must be 4 or 6")
    @Max(value = 6, message = "Capacity must be 4 or 6")
    private Integer capacity;

    @NotBlank(message = "Driver name is required")
    private String driverName;

    @NotBlank(message = "Driver phone is required")
    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Invalid driver phone number")
    private String driverPhone;
}
