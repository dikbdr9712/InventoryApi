package com.api.inventory.controller;

import com.api.inventory.dto.DeliveryDTOs.*;
import com.api.inventory.entity.DeliveryArea;
import com.api.inventory.repository.DeliveryAreaRepository;
import com.api.inventory.security.CurrentUser;
import com.api.inventory.service.AuditService;
import com.api.inventory.service.DeliveryPricingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Delivery prices and delivery areas.
 *   Anyone:            the list of areas, and the delivery price of a cart (the cart page shows it before signing in)
 *   marketplace.manage: add, change and remove areas
 */
@RestController
@RequestMapping("/api/delivery")
public class DeliveryController {

    private final DeliveryPricingService pricing;
    private final DeliveryAreaRepository areas;
    private final AuditService audit;

    public DeliveryController(DeliveryPricingService pricing, DeliveryAreaRepository areas, AuditService audit) {
        this.pricing = pricing;
        this.areas = areas;
        this.audit = audit;
    }

    @GetMapping("/areas")
    public List<AreaOption> areaOptions() {
        return areas.findByActiveTrueOrderByTownAscNameAsc().stream()
                .map(a -> new AreaOption(a.getId(), a.getName(), a.getTown()))
                .toList();
    }

    @PostMapping("/quote")
    public DeliveryQuote quote(@RequestBody QuoteRequest request) {
        if (request == null || request.items() == null || request.items().size() > 200) {
            throw new IllegalArgumentException("Send the products in the cart (at most 200).");
        }
        return pricing.quote(request);
    }

    // ================= Admin =================

    @PreAuthorize("hasAuthority('marketplace.manage')")
    @GetMapping("/admin/areas")
    public List<AreaView> allAreas() {
        return areas.findAllByOrderByTownAscNameAsc().stream().map(DeliveryController::view).toList();
    }

    @PreAuthorize("hasAuthority('marketplace.manage')")
    @PostMapping("/admin/areas")
    @Transactional
    public AreaView addArea(@RequestBody AreaRequest request) {
        DeliveryArea area = new DeliveryArea();
        fill(area, request);
        if (areas.existsByNameIgnoreCaseAndTownIgnoreCase(area.getName(), area.getTown())) {
            throw new IllegalStateException(area.getName() + ", " + area.getTown() + " is already in the list.");
        }
        DeliveryArea saved = areas.save(area);
        audit.record("DELIVERY_AREA_ADDED", "area #" + saved.getId(), saved.getName() + ", " + saved.getTown());
        return view(saved);
    }

    @PreAuthorize("hasAuthority('marketplace.manage')")
    @PutMapping("/admin/areas/{id}")
    @Transactional
    public AreaView updateArea(@PathVariable Long id, @RequestBody AreaRequest request) {
        DeliveryArea area = areas.findById(id).orElseThrow(() -> new IllegalStateException("Area not found."));
        fill(area, request);
        audit.record("DELIVERY_AREA_CHANGED", "area #" + id, area.getName() + ", " + area.getTown() + (area.isActive() ? "" : " (hidden)"));
        return view(areas.save(area));
    }

    @PreAuthorize("hasAuthority('marketplace.manage')")
    @DeleteMapping("/admin/areas/{id}")
    @Transactional
    public ResponseEntity<Void> deleteArea(@PathVariable Long id) {
        DeliveryArea area = areas.findById(id).orElseThrow(() -> new IllegalStateException("Area not found."));
        areas.delete(area); // orders keep their own copy of the map point, so nothing else depends on it
        audit.record("DELIVERY_AREA_REMOVED", "area #" + id, area.getName() + ", " + area.getTown());
        return ResponseEntity.noContent().build();
    }

    private static void fill(DeliveryArea area, AreaRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Send the area's name, town and map point.");
        }
        area.setName(text(request.name(), "Area name"));
        area.setTown(text(request.town(), "Town"));
        DeliveryPricingService.Point point = DeliveryPricingService.point(request.latitude(), request.longitude());
        if (point == null) {
            throw new IllegalStateException("Set the area's map point (latitude and longitude).");
        }
        area.setLatitude(point.latitude());
        area.setLongitude(point.longitude());
        if (request.active() != null) {
            area.setActive(request.active());
        }
        area.setUpdatedBy(CurrentUser.email());
    }

    private static String text(String value, String label) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            throw new IllegalStateException(label + " is required.");
        }
        return v.substring(0, Math.min(80, v.length()));
    }

    private static AreaView view(DeliveryArea a) {
        return new AreaView(a.getId(), a.getName(), a.getTown(), a.getLatitude(), a.getLongitude(), a.isActive());
    }
}
