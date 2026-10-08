package com.api.inventory.controller;

import com.api.inventory.entity.ItemMaster;
import com.api.inventory.entity.SellerProfile;
import com.api.inventory.exception.ResourceNotFoundException;
import com.api.inventory.repository.ItemMasterRepository;
import com.api.inventory.repository.ProductReviewRepository;
import com.api.inventory.repository.SellerProfileRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A seller's shop page (public): who they are, where, since when, and how buyers rate their products. Only for
 * approved sellers. The website shows their products from the product list.
 */
@RestController
@RequestMapping("/api/sellers")
public class SellerShopController {

    private final SellerProfileRepository sellers;
    private final ItemMasterRepository items;
    private final ProductReviewRepository reviews;

    public SellerShopController(SellerProfileRepository sellers, ItemMasterRepository items, ProductReviewRepository reviews) {
        this.sellers = sellers;
        this.items = items;
        this.reviews = reviews;
    }

    /** rating: the average of all shown reviews of the seller's products (0 when none). */
    public record ShopView(Long id, String shopName, String town, String description, Instant since, BigDecimal rating,
                           long ratingCount, long products) {
    }

    @GetMapping("/{id}")
    public ShopView shop(@PathVariable Long id) {
        SellerProfile seller = sellers.findById(id).filter(SellerProfile::isApproved)
                .orElseThrow(() -> new ResourceNotFoundException("This shop was not found."));
        Set<Long> theirs = items.findAll().stream()
                .filter(i -> id.equals(i.getSellerId()) && !Boolean.FALSE.equals(i.getIsActive()))
                .map(ItemMaster::getItemId).collect(Collectors.toSet());
        double sum = 0;
        long count = 0;
        for (Object[] row : reviews.summaries()) { // [itemId, average, count]
            if (theirs.contains(((Number) row[0]).longValue())) {
                long n = ((Number) row[2]).longValue();
                sum += ((Number) row[1]).doubleValue() * n;
                count += n;
            }
        }
        BigDecimal rating = count == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(sum / count).setScale(1, RoundingMode.HALF_UP);
        return new ShopView(seller.getId(), seller.getShopName(), seller.getTown(), seller.getDescription(),
                seller.getCreatedAt(), rating, count, theirs.size());
    }
}
