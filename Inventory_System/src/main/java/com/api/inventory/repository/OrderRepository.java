package com.api.inventory.repository;

import com.api.inventory.dto.SalesReportDTO;
import com.api.inventory.entity.Order;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

	List<Order> findByCustomerEmail(String customerEmail);
	List<Order> findAllByOrderByCreatedAtDesc();
	List<Order> findByOrderStatus(String status);
	List<Order> findBySource(String source);
	List<Order> findByShiftId(Long shiftId);
	java.util.Optional<Order> findByClientRef(String clientRef);
	List<Order> findByCustomerPhoneOrderByCreatedAtDesc(String customerPhone);
	List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
	List<Order> findByCustomerIdIsNull();

	/** Per customer: number of orders, money spent (cancelled orders not counted). */
	@org.springframework.data.jpa.repository.Query("select o.customerId, count(o), sum(o.totalAmount) from Order o where o.customerId in :ids and (o.orderStatus is null or o.orderStatus <> 'CANCELLED') group by o.customerId")
	List<Object[]> statsForCustomers(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);

	/** Sets only the customer of an order (its "updated at" time stays as it was). */
	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("update Order o set o.customerId = :customerId where o.orderId = :orderId")
	int linkCustomer(@org.springframework.data.repository.query.Param("orderId") Long orderId, @org.springframework.data.repository.query.Param("customerId") Long customerId);
	 @Query("""
		        SELECT NEW com.api.inventory.dto.SalesReportDTO(
		            DATE(o.createdAt),
		            SUM(o.totalAmount),
		            COUNT(o.orderId),
		            COALESCE(SUM(o.taxAmount), 0),
		            COALESCE(SUM(o.discountAmount), 0)
		        )
		        FROM Order o
		        WHERE o.createdAt >= :start AND o.createdAt < :end
		        GROUP BY DATE(o.createdAt)
		        ORDER BY DATE(o.createdAt) DESC
		        """)
		    List<SalesReportDTO> findSalesReportByDateRange(@Param("start") LocalDateTime start,
		                                                   @Param("end") LocalDateTime end);
	}