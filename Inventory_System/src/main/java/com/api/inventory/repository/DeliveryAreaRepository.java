package com.api.inventory.repository;

import com.api.inventory.entity.DeliveryArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryAreaRepository extends JpaRepository<DeliveryArea, Long> {

    List<DeliveryArea> findByActiveTrueOrderByTownAscNameAsc();

    List<DeliveryArea> findAllByOrderByTownAscNameAsc();

    boolean existsByNameIgnoreCaseAndTownIgnoreCase(String name, String town);
}
