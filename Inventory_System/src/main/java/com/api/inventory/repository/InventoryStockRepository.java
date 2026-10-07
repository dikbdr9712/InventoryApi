package com.api.inventory.repository;

import com.api.inventory.entity.InventoryStock;

import jakarta.transaction.Transactional;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryStockRepository extends JpaRepository<InventoryStock, Long> {
    Optional<InventoryStock> findByItemId(Long itemId);

    @Query("select s.currentQuantity from InventoryStock s where s.itemId = :itemId")
    Integer quantityNow(@Param("itemId") Long itemId);
    List<InventoryStock> findByItemIdIn(List<Long> itemIds);

    @Modifying
    @Transactional
    @Query("UPDATE InventoryStock s SET s.currentQuantity = s.currentQuantity + :delta WHERE s.itemId = :itemId")
    void adjustStockByDelta(@Param("itemId") Long itemId, @Param("delta") Integer delta);
    
    /** Takes stock only if enough is left, in one step (two tills selling the last one cannot both succeed). 1 = done, 0 = not enough. */
    @Modifying
    @Transactional
    @Query("UPDATE InventoryStock s SET s.currentQuantity = s.currentQuantity - :qty WHERE s.itemId = :itemId AND s.currentQuantity >= :qty")
    int takeIfAvailable(@Param("itemId") Long itemId, @Param("qty") Integer qty);

    /** Available / Unavailable from the quantity, and the time of the change. */
    @Modifying
    @Transactional
    @Query("UPDATE InventoryStock s SET s.status = CASE WHEN s.currentQuantity > 0 THEN 'Available' ELSE 'Unavailable' END, s.lastUpdated = :now WHERE s.itemId = :itemId")
    void refreshStatus(@Param("itemId") Long itemId, @Param("now") java.time.LocalDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE InventoryStock s SET s.currentQuantity = :qty WHERE s.itemId = :itemId")
    void setQuantity(@Param("itemId") Long itemId, @Param("qty") Integer qty);

    @Modifying
    @Query("DELETE FROM InventoryStock s WHERE s.itemId = :itemId")
    void deleteByItemId(@Param("itemId") Long itemId);
}