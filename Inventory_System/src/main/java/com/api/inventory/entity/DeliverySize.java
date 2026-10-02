package com.api.inventory.entity;

import java.util.Locale;

/**
 * How big a product is to deliver. It decides the price per km and which vehicle can carry it.
 * A package takes the size of its biggest product.
 *
 *   SMALL   fits in a delivery bag (medicines, cosmetics, a phone)        any rider, even on foot
 *   MEDIUM  a shoe box or small appliance, still fine on a motorbike       motorbike, scooter or bigger
 *   LARGE   needs a car (microwave, TV, chair, a big carton)              car, taxi or bigger
 *   BULKY   needs a pickup truck and two people (washing machine, fridge)  pickup truck or van
 */
public enum DeliverySize {
    SMALL("Small", "Fits in a delivery bag"),
    MEDIUM("Medium", "Fits on a motorbike"),
    LARGE("Large", "Needs a car"),
    BULKY("Bulky", "Needs a pickup truck");

    public final String label;
    public final String vehicle;

    DeliverySize(String label, String vehicle) {
        this.label = label;
        this.vehicle = vehicle;
    }

    /** Products saved before sizes existed (empty) count as small. */
    public static DeliverySize of(String value) {
        if (value == null || value.isBlank()) {
            return SMALL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SMALL;
        }
    }

    /** For saving: only the four known names, anything else is refused. */
    public static String parse(String value) {
        if (value == null || value.isBlank()) {
            return SMALL.name();
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Delivery size must be Small, Medium, Large or Bulky.");
        }
    }

    public DeliverySize max(DeliverySize other) {
        return other != null && other.ordinal() > ordinal() ? other : this;
    }

    /** The biggest size a rider's vehicle can carry. */
    public static DeliverySize carriedBy(String vehicleType) {
        String v = vehicleType == null ? "" : vehicleType.trim().toLowerCase(Locale.ROOT);
        if (v.contains("pickup") || v.contains("truck") || v.contains("van") || v.contains("bolero")) {
            return BULKY;
        }
        if (v.contains("car") || v.contains("taxi") || v.contains("suv")) {
            return LARGE;
        }
        if (v.contains("motor") || v.contains("scooter") || (v.contains("bike") && !v.contains("bicycle"))) {
            return MEDIUM;
        }
        return SMALL; // bicycle, on foot, anything unknown
    }

    public boolean fits(String vehicleType) {
        return ordinal() <= carriedBy(vehicleType).ordinal();
    }
}
