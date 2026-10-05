// src/main/java/com/api/inventory/repository/PaymentRepository.java

package com.api.inventory.repository;

import com.api.inventory.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
	Optional<Payment> findByOrderId(Long orderId);

	java.util.List<Payment> findByOrderIdIn(java.util.Collection<Long> orderIds);

	/** A journal number already given for another order (see JournalNumbers). */
	Optional<Payment> findFirstByJournalNumberIgnoreCase(String journalNumber);
}