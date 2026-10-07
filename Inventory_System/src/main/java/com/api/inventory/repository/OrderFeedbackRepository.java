package com.api.inventory.repository;

import com.api.inventory.entity.OrderFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OrderFeedbackRepository extends JpaRepository<OrderFeedback, Long> {

    Optional<OrderFeedback> findByOrderId(Long orderId);

    List<OrderFeedback> findByHiddenFalseOrderByCreatedAtDesc();

    List<OrderFeedback> findAllByOrderByCreatedAtDesc();

    /** [riderId, average delivery rating, count] of the shown feedback per driver. */
    @Query("select f.riderId, avg(f.deliveryRating), count(f) from OrderFeedback f where f.hidden = false and f.riderId is not null group by f.riderId")
    List<Object[]> riderAverages();
}
