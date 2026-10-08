package com.api.inventory.controller;

import com.api.inventory.entity.ItemMaster;
import com.api.inventory.repository.ItemMasterRepository;
import com.api.inventory.security.CurrentUser;
import com.api.inventory.service.CouponService;
import com.api.inventory.service.CouponService.CouponForm;
import com.api.inventory.service.CouponService.CouponView;
import com.api.inventory.service.CouponService.Quote;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Coupon codes. Customers (signed in): POST /api/coupons/check with the cart, to see what a code takes off.
 * Staff with "Run offers": make, change, switch off and delete codes (/api/coupons/admin).
 */
@RestController
public class CouponController {

    private final CouponService coupons;
    private final ItemMasterRepository items;

    public CouponController(CouponService coupons, ItemMasterRepository items) {
        this.coupons = coupons;
        this.items = items;
    }

    public record CheckItem(Long itemId, Integer quantity) {
    }

    public record CheckRequest(String code, List<CheckItem> items) {
    }

    /** The items are priced from the shop's own prices, the same way the order will be. */
    @PostMapping("/api/coupons/check")
    public Quote check(@RequestBody CheckRequest request) {
        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (CheckItem line : request == null || request.items() == null ? List.<CheckItem>of() : request.items()) {
            if (line == null || line.itemId() == null || line.quantity() == null || line.quantity() <= 0) {
                continue;
            }
            BigDecimal price = items.findById(line.itemId()).map(ItemMaster::getSellingPrice).orElse(BigDecimal.ZERO);
            itemsTotal = itemsTotal.add(price.multiply(BigDecimal.valueOf(line.quantity())));
        }
        return coupons.check(request == null ? null : request.code(), CurrentUser.email(), itemsTotal);
    }

    @GetMapping("/api/coupons/admin")
    @PreAuthorize("hasAuthority('offers.manage')")
    public List<CouponView> list() {
        return coupons.list();
    }

    @PostMapping("/api/coupons/admin")
    @PreAuthorize("hasAuthority('offers.manage')")
    public CouponView create(@RequestBody CouponForm form) {
        return coupons.create(form, CurrentUser.email());
    }

    @PutMapping("/api/coupons/admin/{id}")
    @PreAuthorize("hasAuthority('offers.manage')")
    public CouponView update(@PathVariable Long id, @RequestBody CouponForm form) {
        return coupons.update(id, form);
    }

    @DeleteMapping("/api/coupons/admin/{id}")
    @PreAuthorize("hasAuthority('offers.manage')")
    public void delete(@PathVariable Long id) {
        coupons.delete(id);
    }
}
