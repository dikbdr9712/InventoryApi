package com.api.inventory.dto;

import java.math.BigDecimal;
import java.util.List;

/** Delivery prices, delivery areas and map points. */
public final class DeliveryDTOs {

    private DeliveryDTOs() {
    }

    // ---------- Rates (part of the marketplace settings) ----------

    public record RateView(String size, String label, String vehicle, BigDecimal baseFee, BigDecimal perKm) {
    }

    public record RateRequest(String size, BigDecimal baseFee, BigDecimal perKm) {
    }

    // ---------- Price check before ordering ----------

    public record QuoteItem(Long itemId, Integer quantity) {
    }

    /** Where to deliver: the phone's location (latitude/longitude) or a delivery area. Neither = estimated price. */
    public record QuoteRequest(List<QuoteItem> items, Double latitude, Double longitude, Long areaId) {
    }

    /** One package: one seller's products, carried by one rider. */
    public record PackageQuote(String sellerName, String town, String size, String sizeLabel, String vehicle,
                               BigDecimal distanceKm, boolean estimated, BigDecimal fee) {
    }

    /**
     * located = the customer gave a location. problem = why the order cannot be delivered (for example too far);
     * the order is refused while there is a problem.
     */
    public record DeliveryQuote(List<PackageQuote> packages, BigDecimal totalFee, String location, boolean located,
                                String problem) {
    }

    // ---------- Delivery areas ----------

    /** What a customer sees in the area list. */
    public record AreaOption(Long id, String name, String town) {
    }

    public record AreaView(Long id, String name, String town, Double latitude, Double longitude, boolean active) {
    }

    public record AreaRequest(String name, String town, Double latitude, Double longitude, Boolean active) {
    }

    // ---------- Map points ----------

    public record LocationRequest(Double latitude, Double longitude) {
    }
}
