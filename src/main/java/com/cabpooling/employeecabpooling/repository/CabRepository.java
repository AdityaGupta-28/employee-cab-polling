package com.cabpooling.employeecabpooling.repository;

import com.cabpooling.employeecabpooling.model.entity.Cab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CabRepository extends JpaRepository<Cab, Long> {
    Optional<Cab> findByLicensePlate(String licensePlate);
    boolean existsByLicensePlate(String licensePlate);
    List<Cab> findByIsActiveTrue();
    List<Cab> findByIsActiveTrueAndCapacityGreaterThanEqual(Integer capacity);
}
