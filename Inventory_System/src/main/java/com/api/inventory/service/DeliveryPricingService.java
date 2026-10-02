package com.api.inventory.service;

import com.api.inventory.dto.DeliveryDTOs.*;
import com.api.inventory.entity.*;
import com.api.inventory.repository.DeliveryAreaRepository;
import com.api.inventory.repository.ItemMasterRepository;
import com.api.inventory.repository.SellerProfileRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * What a delivery costs, worked out on the server only (the browser never decides a price).
 *
 *   fee       = base fee for the package's size + (road km beyond the included km) x the size's rate per km,
 *               rounded up to the next Nu. 5
 *   rider pay = riderSharePercent of the fee, rounded to whole Nu.; the rest pays for running the service
 *
 * Road km = straight-line distance between the pickup and drop points x ROAD_FACTOR (mountain roads wind).
 * When a point is missing (a seller has not set their location, or the customer gave none) the fee uses the
 * "unknown distance" from the settings and is marked as estimated, so the rider knows to call.
 */
@Service
public class DeliveryPricingService {

    /** A road between two points is about this much longer than the straight line. */
    public static final double ROAD_FACTOR = 1.35;

    // Defaults for settings that were never saved (Nu.). Index = DeliverySize.ordinal()
    private static final BigDecimal[] DEFAULT_BASE = { bd("50"), bd("80"), bd("200"), bd("500") };
    private static final BigDecimal[] DEFAULT_PER_KM = { bd("10"), bd("12"), bd("20"), bd("35") };
    private static final BigDecimal DEFAULT_INCLUDED_KM = bd("2");
    private static final BigDecimal DEFAULT_RIDER_SHARE = bd("80");
    private static final BigDecimal DEFAULT_MAX_KM = bd("30");
    private static final BigDecimal DEFAULT_UNKNOWN_KM = bd("5");
    private static final BigDecimal FIVE = bd("5");

    public record Point(double latitude, double longitude) {
    }

    /** The rates in force, with the defaults filled in. */
    public record Tariff(Map<DeliverySize, BigDecimal> baseFee, Map<DeliverySize, BigDecimal> perKm, BigDecimal includedKm,
                         BigDecimal riderSharePercent, BigDecimal maxDistanceKm, BigDecimal unknownDistanceKm) {
    }

    public record Price(DeliverySize size, BigDecimal distanceKm, boolean estimated, BigDecimal fee, BigDecimal riderPay) {
    }

    /** Where to drop, and how it was found. point = null when the customer gave no location. */
    public record Drop(Point point, String label) {
    }

    /** One seller's share of an order, ready to price. seller = null for our own shop. */
    public record PlannedPackage(SellerProfile seller, Point pickup, String pickupAddress, Price price) {
    }

    private final MarketplaceService marketplace;
    private final DeliveryAreaRepository areas;
    private final ItemMasterRepository items;
    private final SellerProfileRepository sellers;

    public DeliveryPricingService(MarketplaceService marketplace, DeliveryAreaRepository areas,
                                  ItemMasterRepository items, SellerProfileRepository sellers) {
        this.marketplace = marketplace;
        this.areas = areas;
        this.items = items;
        this.sellers = sellers;
    }

    // ================= Rates =================

    public static Tariff tariffOf(MarketplaceSettings s) {
        Map<DeliverySize, BigDecimal> base = new EnumMap<>(DeliverySize.class);
        Map<DeliverySize, BigDecimal> perKm = new EnumMap<>(DeliverySize.class);
        BigDecimal[] savedBase = { s.getSmallBaseFee(), s.getMediumBaseFee(), s.getLargeBaseFee(), s.getBulkyBaseFee() };
        BigDecimal[] savedPerKm = { s.getSmallPerKm(), s.getMediumPerKm(), s.getLargePerKm(), s.getBulkyPerKm() };
        for (DeliverySize size : DeliverySize.values()) {
            base.put(size, or(savedBase[size.ordinal()], DEFAULT_BASE[size.ordinal()]));
            perKm.put(size, or(savedPerKm[size.ordinal()], DEFAULT_PER_KM[size.ordinal()]));
        }
        return new Tariff(base, perKm, or(s.getIncludedKm(), DEFAULT_INCLUDED_KM), or(s.getRiderSharePercent(), DEFAULT_RIDER_SHARE),
                or(s.getMaxDistanceKm(), DEFAULT_MAX_KM), or(s.getUnknownDistanceKm(), DEFAULT_UNKNOWN_KM));
    }

    public Tariff tariff() {
        return tariffOf(marketplace.settings());
    }

    /** The price of one package. Throws when the drop point is further than we deliver. */
    public Price price(DeliverySize size, Point pickup, Point drop) {
        Tariff t = tariff();
        boolean estimated = pickup == null || drop == null;
        BigDecimal km = estimated
                ? t.unknownDistanceKm()
                : BigDecimal.valueOf(roadKm(pickup, drop)).setScale(1, RoundingMode.HALF_UP);
        if (!estimated && km.compareTo(t.maxDistanceKm()) > 0) {
            throw new IllegalStateException("This address is about " + km.stripTrailingZeros().toPlainString()
                    + " km from the seller. We deliver up to " + t.maxDistanceKm().stripTrailingZeros().toPlainString() + " km.");
        }
        BigDecimal extraKm = km.subtract(t.includedKm()).max(BigDecimal.ZERO);
        BigDecimal raw = t.baseFee().get(size).add(extraKm.multiply(t.perKm().get(size)));
        // round up to the next Nu. 5, so prices look tidy and never under-charge
        BigDecimal fee = raw.divide(FIVE, 0, RoundingMode.CEILING).multiply(FIVE).setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal riderPay = fee.multiply(t.riderSharePercent()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.UNNECESSARY);
        return new Price(size, km, estimated, fee, riderPay);
    }

    /** Straight-line distance in km (haversine). */
    public static double straightKm(Point a, Point b) {
        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLng = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.latitude())) * Math.cos(Math.toRadians(b.latitude())) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    public static double roadKm(Point a, Point b) {
        return straightKm(a, b) * ROAD_FACTOR;
    }

    // ================= Points =================

    /** A point from two numbers, or null when either is missing. Refuses numbers that cannot be on a map. */
    public static Point point(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        if (latitude.isNaN() || longitude.isNaN() || Math.abs(latitude) > 90 || Math.abs(longitude) > 180
                || (latitude == 0 && longitude == 0)) {
            throw new IllegalArgumentException("That location is not valid. Please try again.");
        }
        return new Point(latitude, longitude);
    }

    /** The customer's drop point: a delivery area wins over raw numbers, because the customer chose it on purpose. */
    public Drop drop(Double latitude, Double longitude, Long areaId) {
        if (areaId != null) {
            DeliveryArea area = areas.findById(areaId).filter(DeliveryArea::isActive)
                    .orElseThrow(() -> new IllegalStateException("That delivery area is no longer available. Please choose another."));
            return new Drop(new Point(area.getLatitude(), area.getLongitude()), area.getName() + ", " + area.getTown());
        }
        Point p = point(latitude, longitude);
        return new Drop(p, p == null ? null : "Your location (GPS)");
    }

    public Point pickupOf(SellerProfile seller) {
        if (seller != null) {
            return seller.getPickupLatitude() == null || seller.getPickupLongitude() == null
                    ? null : new Point(seller.getPickupLatitude(), seller.getPickupLongitude());
        }
        MarketplaceSettings s = marketplace.settings();
        return s.getShopLatitude() == null || s.getShopLongitude() == null ? null : new Point(s.getShopLatitude(), s.getShopLongitude());
    }

    public String pickupAddressOf(SellerProfile seller) {
        if (seller != null) {
            return seller.getPickupAddress() + (seller.getTown() == null ? "" : ", " + seller.getTown());
        }
        String shop = marketplace.settings().getShopAddress();
        return shop == null || shop.isBlank() ? "DK/Phar shop" : "DK/Phar shop, " + shop;
    }

    // ================= Planning an order =================

    /** Prices one seller's group of products going to one drop point. */
    public PlannedPackage plan(SellerProfile seller, Collection<ItemMaster> products, Point drop) {
        DeliverySize size = DeliverySize.SMALL;
        for (ItemMaster product : products) {
            size = size.max(DeliverySize.of(product.getDeliverySize()));
        }
        Point pickup = pickupOf(seller);
        return new PlannedPackage(seller, pickup, pickupAddressOf(seller), price(size, pickup, drop));
    }

    /** The delivery price of a cart, before ordering. Problems come back as text instead of an error. */
    public DeliveryQuote quote(QuoteRequest request) {
        Drop drop;
        try {
            drop = drop(request.latitude(), request.longitude(), request.areaId());
        } catch (RuntimeException e) {
            return new DeliveryQuote(List.of(), BigDecimal.ZERO, null, false, e.getMessage());
        }

        Map<Long, List<ItemMaster>> bySeller = new LinkedHashMap<>(); // key 0 = our own shop
        for (QuoteItem line : request.items() == null ? List.<QuoteItem>of() : request.items()) {
            if (line == null || line.itemId() == null) {
                continue;
            }
            items.findById(line.itemId()).filter(i -> !Boolean.FALSE.equals(i.getIsActive())).ifPresent(i ->
                    bySeller.computeIfAbsent(i.getSellerId() == null ? 0L : i.getSellerId(), k -> new ArrayList<>()).add(i));
        }

        List<PackageQuote> result = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        try {
            for (Map.Entry<Long, List<ItemMaster>> group : bySeller.entrySet()) {
                SellerProfile seller = group.getKey() == 0L ? null : sellers.findById(group.getKey()).orElse(null);
                PlannedPackage planned = plan(seller, group.getValue(), drop.point());
                Price p = planned.price();
                result.add(new PackageQuote(seller == null ? "DK/Phar" : seller.getShopName(), seller == null ? null : seller.getTown(),
                        p.size().name(), p.size().label, p.size().vehicle, p.distanceKm(), p.estimated(), p.fee()));
                total = total.add(p.fee());
            }
        } catch (IllegalStateException tooFar) {
            return new DeliveryQuote(result, total, drop.label(), drop.point() != null, tooFar.getMessage());
        }
        return new DeliveryQuote(result, total, drop.label(), drop.point() != null, null);
    }

    // ================= Helpers =================

    private static BigDecimal or(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
