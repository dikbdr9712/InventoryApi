package com.api.inventory.repository;

import com.api.inventory.entity.SellerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SellerProfileRepository extends JpaRepository<SellerProfile, Long> {
    Optional<SellerProfile> findByUserEmail(String email);
    List<SellerProfile> findAllByOrderByCreatedAtDesc();
    List<SellerProfile> findByCidNumber(String cidNumber);
    long countByStatus(String status);
}
