package com.cabpooling.employeecabpooling.service;

import com.cabpooling.employeecabpooling.dto.assignment.EscortRequest;
import com.cabpooling.employeecabpooling.dto.assignment.EscortResponse;
import com.cabpooling.employeecabpooling.dto.assignment.EscortStatusRequest;
import com.cabpooling.employeecabpooling.exception.DuplicateResourceException;
import com.cabpooling.employeecabpooling.exception.ResourceNotFoundException;
import com.cabpooling.employeecabpooling.model.entity.CabAssignment;
import com.cabpooling.employeecabpooling.model.entity.EscortAssignment;
import com.cabpooling.employeecabpooling.model.enums.EscortStatus;
import com.cabpooling.employeecabpooling.repository.CabAssignmentRepository;
import com.cabpooling.employeecabpooling.repository.EscortAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EscortService {

    private final EscortAssignmentRepository escortAssignmentRepository;
    private final CabAssignmentRepository cabAssignmentRepository;

    @Transactional
    public EscortResponse create(EscortRequest request) {
        CabAssignment assignment = cabAssignmentRepository.findById(request.getCabAssignmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cab assignment not found with id: " + request.getCabAssignmentId()));

        if (escortAssignmentRepository.existsByCabAssignmentId(request.getCabAssignmentId())) {
            throw new DuplicateResourceException(
                    "Escort already assigned to cab assignment id: " + request.getCabAssignmentId());
        }

        EscortAssignment escort = EscortAssignment.builder()
                .cabAssignment(assignment)
                .escortName(request.getEscortName())
                .escortContact(request.getEscortContact())
                .status(EscortStatus.ASSIGNED)
                .build();

        EscortAssignment saved = escortAssignmentRepository.save(escort);

        assignment.setHasEscort(true);
        cabAssignmentRepository.save(assignment);

        log.info("Assigned escort id={} ({}) to cab assignment id={}",
                saved.getId(), saved.getEscortName(), assignment.getId());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<EscortResponse> findAll() {
        return escortAssignmentRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EscortResponse findById(Long id) {
        EscortAssignment escort = escortAssignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Escort assignment not found with id: " + id));
        return mapToResponse(escort);
    }

    @Transactional(readOnly = true)
    public EscortResponse findByCabAssignmentId(Long cabAssignmentId) {
        EscortAssignment escort = escortAssignmentRepository.findByCabAssignmentId(cabAssignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Escort not found for cab assignment id: " + cabAssignmentId));
        return mapToResponse(escort);
    }

    @Transactional
    public EscortResponse updateStatus(Long id, EscortStatusRequest request) {
        EscortAssignment escort = escortAssignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Escort assignment not found with id: " + id));

        escort.setStatus(request.getStatus());

        CabAssignment assignment = escort.getCabAssignment();
        if (request.getStatus() == EscortStatus.CANCELLED) {
            assignment.setHasEscort(false);
        } else {
            assignment.setHasEscort(true);
        }
        cabAssignmentRepository.save(assignment);

        EscortAssignment updated = escortAssignmentRepository.save(escort);
        log.info("Updated escort assignment id={} status to {}", updated.getId(), request.getStatus());
        return mapToResponse(updated);
    }

    @Transactional
    public void delete(Long id) {
        EscortAssignment escort = escortAssignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Escort assignment not found with id: " + id));

        CabAssignment assignment = escort.getCabAssignment();
        assignment.setHasEscort(false);
        cabAssignmentRepository.save(assignment);

        escortAssignmentRepository.delete(escort);
        log.info("Deleted escort assignment id={} and updated cab assignment id={} hasEscort=false",
                id, assignment.getId());
    }

    private EscortResponse mapToResponse(EscortAssignment escort) {
        return EscortResponse.builder()
                .id(escort.getId())
                .cabAssignmentId(escort.getCabAssignment() != null ? escort.getCabAssignment().getId() : null)
                .escortName(escort.getEscortName())
                .escortContact(escort.getEscortContact())
                .status(escort.getStatus())
                .createdAt(escort.getCreatedAt())
                .updatedAt(escort.getUpdatedAt())
                .build();
    }
}
