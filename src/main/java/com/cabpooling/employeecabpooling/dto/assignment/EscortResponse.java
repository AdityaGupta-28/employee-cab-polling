package com.cabpooling.employeecabpooling.dto.assignment;

import com.cabpooling.employeecabpooling.model.enums.EscortStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscortResponse {

    private Long id;
    private Long cabAssignmentId;
    private String escortName;
    private String escortContact;
    private EscortStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
