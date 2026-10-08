package com.api.inventory.repository;

import com.api.inventory.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    List<CustomerAddress> findByUserEmailIgnoreCaseOrderByDefaultAddressDescCreatedAtDesc(String userEmail);

    Optional<CustomerAddress> findByIdAndUserEmailIgnoreCase(Long id, String userEmail);

    long countByUserEmailIgnoreCase(String userEmail);
}
