package com.cabpooling.employeecabpooling.repository;

import com.cabpooling.employeecabpooling.model.entity.EscortAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EscortAssignmentRepository extends JpaRepository<EscortAssignment, Long> {
    Optional<EscortAssignment> findByCabAssignmentId(Long cabAssignmentId);
    boolean existsByCabAssignmentId(Long cabAssignmentId);
}
