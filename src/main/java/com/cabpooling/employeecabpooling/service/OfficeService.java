package com.cabpooling.employeecabpooling.service;

import com.cabpooling.employeecabpooling.dto.office.OfficeRequest;
import com.cabpooling.employeecabpooling.dto.office.OfficeResponse;
import com.cabpooling.employeecabpooling.exception.ResourceNotFoundException;
import com.cabpooling.employeecabpooling.model.entity.Office;
import com.cabpooling.employeecabpooling.repository.OfficeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OfficeService {

    private final OfficeRepository officeRepository;

    @Transactional
    public OfficeResponse create(OfficeRequest request) {
        Office office = Office.builder()
                .name(request.getName())
                .address(request.getAddress())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();
        return toResponse(officeRepository.save(office));
    }

    @Transactional(readOnly = true)
    public List<OfficeResponse> findAll() {
        return officeRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OfficeResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional
    public OfficeResponse update(Long id, OfficeRequest request) {
        Office office = getOrThrow(id);
        office.setName(request.getName());
        office.setAddress(request.getAddress());
        office.setLatitude(request.getLatitude());
        office.setLongitude(request.getLongitude());
        return toResponse(officeRepository.save(office));
    }

    @Transactional
    public void delete(Long id) {
        if (!officeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Office not found with id: " + id);
        }
        officeRepository.deleteById(id);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Office getOrThrow(Long id) {
        return officeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Office not found with id: " + id));
    }

    public OfficeResponse toResponse(Office office) {
        return OfficeResponse.builder()
                .id(office.getId())
                .name(office.getName())
                .address(office.getAddress())
                .latitude(office.getLatitude())
                .longitude(office.getLongitude())
                .createdAt(office.getCreatedAt())
                .build();
    }
}
