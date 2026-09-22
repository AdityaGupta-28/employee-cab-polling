package com.cabpooling.employeecabpooling.repository;

import com.cabpooling.employeecabpooling.model.entity.PickupStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PickupStopRepository extends JpaRepository<PickupStop, Long> {
    List<PickupStop> findByCabAssignmentIdOrderByStopOrderAsc(Long cabAssignmentId);
    Optional<PickupStop> findByBookingId(Long bookingId);
}
