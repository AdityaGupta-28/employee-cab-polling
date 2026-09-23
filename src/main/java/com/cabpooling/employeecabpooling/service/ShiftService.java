package com.cabpooling.employeecabpooling.service;

import com.cabpooling.employeecabpooling.dto.shift.ShiftRequest;
import com.cabpooling.employeecabpooling.dto.shift.ShiftResponse;
import com.cabpooling.employeecabpooling.exception.ResourceNotFoundException;
import com.cabpooling.employeecabpooling.model.entity.Office;
import com.cabpooling.employeecabpooling.model.entity.Shift;
import com.cabpooling.employeecabpooling.repository.OfficeRepository;
import com.cabpooling.employeecabpooling.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final OfficeRepository officeRepository;

    @Transactional
    public ShiftResponse create(ShiftRequest request) {
        Office office = officeRepository.findById(request.getOfficeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Office not found with id: " + request.getOfficeId()));

        Shift shift = Shift.builder()
                .office(office)
                .name(request.getName())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .shiftType(request.getShiftType())
                .cutoffMinutes(request.getCutoffMinutes())
                .build();

        return toResponse(shiftRepository.save(shift));
    }

    @Transactional(readOnly = true)
    public List<ShiftResponse> findAll(Long officeId) {
        List<Shift> shifts = (officeId != null)
                ? shiftRepository.findByOfficeId(officeId)
                : shiftRepository.findAll();
        return shifts.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ShiftResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional
    public ShiftResponse update(Long id, ShiftRequest request) {
        Shift shift = getOrThrow(id);

        Office office = officeRepository.findById(request.getOfficeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Office not found with id: " + request.getOfficeId()));

        shift.setOffice(office);
        shift.setName(request.getName());
        shift.setStartTime(request.getStartTime());
        shift.setEndTime(request.getEndTime());
        shift.setShiftType(request.getShiftType());
        shift.setCutoffMinutes(request.getCutoffMinutes());

        return toResponse(shiftRepository.save(shift));
    }

    @Transactional
    public void delete(Long id) {
        if (!shiftRepository.existsById(id)) {
            throw new ResourceNotFoundException("Shift not found with id: " + id);
        }
        shiftRepository.deleteById(id);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    public Shift getOrThrow(Long id) {
        return shiftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found with id: " + id));
    }

    public ShiftResponse toResponse(Shift shift) {
        return ShiftResponse.builder()
                .id(shift.getId())
                .officeId(shift.getOffice().getId())
                .officeName(shift.getOffice().getName())
                .name(shift.getName())
                .startTime(shift.getStartTime())
                .endTime(shift.getEndTime())
                .shiftType(shift.getShiftType())
                .cutoffMinutes(shift.getCutoffMinutes())
                .createdAt(shift.getCreatedAt())
                .build();
    }
}
