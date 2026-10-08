package com.api.inventory.repository;

import com.api.inventory.entity.ItemPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemPhotoRepository extends JpaRepository<ItemPhoto, Long> {

    List<ItemPhoto> findByItemIdOrderByPositionAscIdAsc(Long itemId);

    Optional<ItemPhoto> findByIdAndItemId(Long id, Long itemId);

    long countByItemId(Long itemId);
}
