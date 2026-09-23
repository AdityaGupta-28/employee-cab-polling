package com.cabpooling.employeecabpooling.repository;

import com.cabpooling.employeecabpooling.model.entity.CabAssignment;
import com.cabpooling.employeecabpooling.model.enums.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CabAssignmentRepository extends JpaRepository<CabAssignment, Long> {
    List<CabAssignment> findByAssignmentDateAndShiftId(LocalDate assignmentDate, Long shiftId);
    List<CabAssignment> findByAssignmentDateAndStatus(LocalDate assignmentDate, AssignmentStatus status);
    List<CabAssignment> findByCabIdAndAssignmentDate(Long cabId, LocalDate assignmentDate);
    List<CabAssignment> findByAssignmentDate(LocalDate assignmentDate);
    List<CabAssignment> findByShiftId(Long shiftId);
    boolean existsByCabIdAndShiftIdAndAssignmentDate(Long cabId, Long shiftId, LocalDate assignmentDate);
}
