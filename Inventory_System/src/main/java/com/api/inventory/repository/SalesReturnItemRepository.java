package com.api.inventory.repository;

import com.api.inventory.entity.SalesReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesReturnItemRepository extends JpaRepository<SalesReturnItem, Long> {

    /** How many of one sale line have already come back, so nobody can return more than was sold */
    @Query("select coalesce(sum(i.quantity), 0) from SalesReturnItem i where i.orderItemId = :orderItemId")
    long totalReturnedFor(@Param("orderItemId") Long orderItemId);
}