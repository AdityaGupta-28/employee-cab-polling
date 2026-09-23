package com.cabpooling.employeecabpooling.service;

import com.cabpooling.employeecabpooling.dto.cab.CabRequest;
import com.cabpooling.employeecabpooling.dto.cab.CabResponse;
import com.cabpooling.employeecabpooling.dto.cab.CabStatusRequest;
import com.cabpooling.employeecabpooling.exception.DuplicateResourceException;
import com.cabpooling.employeecabpooling.exception.ResourceNotFoundException;
import com.cabpooling.employeecabpooling.model.entity.Cab;
import com.cabpooling.employeecabpooling.repository.CabRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CabService {

    private final CabRepository cabRepository;

    @Transactional
    public CabResponse create(CabRequest request) {
        if (cabRepository.existsByLicensePlate(request.getLicensePlate())) {
            throw new DuplicateResourceException(
                    "Cab with license plate already exists: " + request.getLicensePlate());
        }
        Cab cab = Cab.builder()
                .licensePlate(request.getLicensePlate())
                .model(request.getModel())
                .capacity(request.getCapacity())
                .driverName(request.getDriverName())
                .driverPhone(request.getDriverPhone())
                .isActive(true)
                .build();
        return toResponse(cabRepository.save(cab));
    }

    @Transactional(readOnly = true)
    public List<CabResponse> findAll(Boolean activeOnly) {
        List<Cab> cabs = Boolean.TRUE.equals(activeOnly)
                ? cabRepository.findByIsActiveTrue()
                : cabRepository.findAll();
        return cabs.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CabResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional
    public CabResponse update(Long id, CabRequest request) {
        Cab cab = getOrThrow(id);

        // Allow updating licensePlate only if it hasn't changed or the new value is unique
        if (!cab.getLicensePlate().equalsIgnoreCase(request.getLicensePlate())
                && cabRepository.existsByLicensePlate(request.getLicensePlate())) {
            throw new DuplicateResourceException(
                    "Cab with license plate already exists: " + request.getLicensePlate());
        }

        cab.setLicensePlate(request.getLicensePlate());
        cab.setModel(request.getModel());
        cab.setCapacity(request.getCapacity());
        cab.setDriverName(request.getDriverName());
        cab.setDriverPhone(request.getDriverPhone());
        return toResponse(cabRepository.save(cab));
    }

    @Transactional
    public CabResponse updateStatus(Long id, CabStatusRequest request) {
        Cab cab = getOrThrow(id);
        cab.setIsActive(request.getIsActive());
        return toResponse(cabRepository.save(cab));
    }

    @Transactional
    public void delete(Long id) {
        if (!cabRepository.existsById(id)) {
            throw new ResourceNotFoundException("Cab not found with id: " + id);
        }
        cabRepository.deleteById(id);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Cab getOrThrow(Long id) {
        return cabRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cab not found with id: " + id));
    }

    public CabResponse toResponse(Cab cab) {
        return CabResponse.builder()
                .id(cab.getId())
                .licensePlate(cab.getLicensePlate())
                .model(cab.getModel())
                .capacity(cab.getCapacity())
                .driverName(cab.getDriverName())
                .driverPhone(cab.getDriverPhone())
                .isActive(cab.getIsActive())
                .createdAt(cab.getCreatedAt())
                .updatedAt(cab.getUpdatedAt())
                .build();
    }
}
