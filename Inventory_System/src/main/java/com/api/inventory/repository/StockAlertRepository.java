package com.api.inventory.repository;

import com.api.inventory.entity.StockAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockAlertRepository extends JpaRepository<StockAlert, Long> {

    /** The person's alerts still waiting for the product to come back. */
    List<StockAlert> findByUserEmailIgnoreCaseAndNotifiedAtIsNull(String userEmail);

    Optional<StockAlert> findByUserEmailIgnoreCaseAndItemId(String userEmail, Long itemId);

    List<StockAlert> findByItemIdAndNotifiedAtIsNull(Long itemId);
}
