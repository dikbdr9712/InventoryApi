package com.api.inventory.repository;

import com.api.inventory.entity.RiderProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiderProfileRepository extends JpaRepository<RiderProfile, Long> {
    Optional<RiderProfile> findByUserEmail(String email);
    List<RiderProfile> findAllByOrderByCreatedAtDesc();
    List<RiderProfile> findByCidNumber(String cidNumber);
}
