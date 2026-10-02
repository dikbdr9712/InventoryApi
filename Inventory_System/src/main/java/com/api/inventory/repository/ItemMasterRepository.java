package com.api.inventory.repository;

import com.api.inventory.entity.ItemMaster;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemMasterRepository extends JpaRepository<ItemMaster, Long> {
	List<ItemMaster> findBySkuContainingIgnoreCaseOrItemNameContainingIgnoreCase(String sku, String itemName);
	List<ItemMaster> findBySellerIdOrderByItemIdDesc(Long sellerId);
	Optional<ItemMaster> findBySku(String sku);

}