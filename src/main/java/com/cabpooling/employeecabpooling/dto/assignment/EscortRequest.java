package com.cabpooling.employeecabpooling.dto.assignment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscortRequest {

    @NotNull(message = "Cab assignment ID is required")
    private Long cabAssignmentId;

    @NotBlank(message = "Escort name is required")
    private String escortName;

    @NotBlank(message = "Escort contact is required")
    private String escortContact;
}
