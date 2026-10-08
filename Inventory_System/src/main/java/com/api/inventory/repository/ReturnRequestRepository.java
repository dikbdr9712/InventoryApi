package com.api.inventory.repository;

import com.api.inventory.entity.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    List<ReturnRequest> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    List<ReturnRequest> findByStatusInOrderByCreatedAtDesc(Collection<String> statuses);

    List<ReturnRequest> findByOrderIdAndStatusIn(Long orderId, Collection<String> statuses);

    long countByStatus(String status);
}
