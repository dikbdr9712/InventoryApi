package com.api.inventory.repository;

import com.api.inventory.entity.ProductReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    Optional<ProductReview> findByUserEmailIgnoreCaseAndItemId(String userEmail, Long itemId);

    List<ProductReview> findByItemIdAndHiddenFalseOrderByCreatedAtDesc(Long itemId);

    List<ProductReview> findByUserEmailIgnoreCaseAndItemIdIn(String userEmail, java.util.Collection<Long> itemIds);

    List<ProductReview> findAllByOrderByCreatedAtDesc();

    /** [itemId, average, count] of the shown reviews of every rated product. */
    @Query("select r.itemId, avg(r.rating), count(r) from ProductReview r where r.hidden = false group by r.itemId")
    List<Object[]> summaries();
}
