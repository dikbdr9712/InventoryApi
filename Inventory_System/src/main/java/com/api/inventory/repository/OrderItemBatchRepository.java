package com.api.inventory.repository;

import com.api.inventory.entity.OrderItemBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemBatchRepository extends JpaRepository<OrderItemBatch, Long> {

    List<OrderItemBatch> findByOrderItemIdOrderByIdDesc(Long orderItemId);

    List<OrderItemBatch> findByBatchId(Long batchId);
}
