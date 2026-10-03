package com.api.inventory.repository;

import com.api.inventory.entity.StockBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockBatchRepository extends JpaRepository<StockBatch, Long> {

    /** What can be sold, in the order it should leave the shelf: first to expire first, undated last, oldest first. Locked. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from StockBatch b where b.itemId = :itemId and b.status = 'ACTIVE' and b.quantityLeft > 0 "
            + "order by b.expiryDate asc nulls last, b.receivedAt asc, b.id asc")
    List<StockBatch> findUsableForUpdate(@Param("itemId") Long itemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from StockBatch b where b.id = :id")
    Optional<StockBatch> findByIdForUpdate(@Param("id") Long id);

    /** [itemId, sum of quantityLeft] over ACTIVE batches. */
    @Query("select b.itemId, coalesce(sum(b.quantityLeft), 0) from StockBatch b where b.status = 'ACTIVE' group by b.itemId")
    List<Object[]> activeTotals();

    List<StockBatch> findByItemIdOrderByReceivedAtDesc(Long itemId);

    /** On the shelf now, first to expire first. */
    @Query("select b from StockBatch b where b.status = 'ACTIVE' and b.quantityLeft > 0 order by b.expiryDate asc nulls last, b.receivedAt asc")
    List<StockBatch> findOnShelf();

    /** On the shelf and expiring on or before this day. */
    @Query("select b from StockBatch b where b.status = 'ACTIVE' and b.quantityLeft > 0 and b.expiryDate is not null "
            + "and b.expiryDate <= :until order by b.expiryDate asc")
    List<StockBatch> findExpiringBy(@Param("until") LocalDate until);

    /** Still on sale but already past its date (taken off sale by StockService). */
    @Query("select b from StockBatch b where b.status = 'ACTIVE' and b.quantityLeft > 0 and b.expiryDate is not null "
            + "and b.expiryDate < :today order by b.itemId, b.expiryDate")
    List<StockBatch> findDueToExpire(@Param("today") LocalDate today);

    List<StockBatch> findByStatusOrderByExpiryDateDesc(String status);
}
