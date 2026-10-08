package com.api.inventory.repository;

import com.api.inventory.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    List<WishlistItem> findByUserEmailIgnoreCaseOrderByCreatedAtDesc(String userEmail);

    Optional<WishlistItem> findByUserEmailIgnoreCaseAndItemId(String userEmail, Long itemId);

    long countByUserEmailIgnoreCase(String userEmail);
}
