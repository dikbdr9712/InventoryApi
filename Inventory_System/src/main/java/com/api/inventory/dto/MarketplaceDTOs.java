package com.api.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * What the marketplace screens send and receive. Grouped in one file because each is small.
 * Requests are filled from JSON; views are what the server sends back (never entities, so nothing private leaks).
 */
public final class MarketplaceDTOs {

    private MarketplaceDTOs() {
    }

    // ---------- Settings ----------

    /** Commission and delivery rates. Public: customers, sellers and riders can all see how prices are made. */
    public record SettingsView(BigDecimal defaultCommissionPercent, List<DeliveryDTOs.RateView> rates, BigDecimal includedKm,
                               BigDecimal riderSharePercent, BigDecimal maxDistanceKm, BigDecimal unknownDistanceKm,
                               String shopAddress, Double shopLatitude, Double shopLongitude,
                               Instant updatedAt, String updatedBy) {
    }

    public record SettingsRequest(BigDecimal defaultCommissionPercent, List<DeliveryDTOs.RateRequest> rates, BigDecimal includedKm,
                                  BigDecimal riderSharePercent, BigDecimal maxDistanceKm, BigDecimal unknownDistanceKm,
                                  String shopAddress, Double shopLatitude, Double shopLongitude) {
    }

    // ---------- Applications ----------

    public record SellerApplication(String shopName, String phone, String pickupAddress, String town, String description,
                                    String bankName, String bankAccountName, String bankAccountNumber,
                                    String cidNumber, String tradeLicenseNumber, String tpnNumber,
                                    Integer acceptedTermsVersion, Boolean confirmTrue) {
    }

    public record RiderApplication(String phone, String vehicleType, String vehicleNumber, String licenseNumber, String town,
                                   String bankName, String bankAccountName, String bankAccountNumber,
                                   String cidNumber, java.time.LocalDate licenseExpiry,
                                   String emergencyContactName, String emergencyContactPhone,
                                   Integer acceptedTermsVersion, Boolean confirmTrue) {
    }

    /** Identity checks, the agreement accepted, and the documents sent (shown to admins and to the person). */
    public record Verification(String cidNumber, String tradeLicenseNumber, String tpnNumber,
                               java.time.LocalDate licenseExpiry, boolean licenceExpired,
                               String emergencyContactName, String emergencyContactPhone,
                               Integer termsVersion, Instant termsAcceptedAt,
                               List<com.api.inventory.service.PartnerDocumentService.DocumentView> documents) {
    }

    /** One seller or rider, as the person themselves or an admin sees them. */
    public record PartnerView(Long id, String type, String status, String statusNote, String name, String email, String phone,
                              String shopName, String pickupAddress, String town, String description,
                              Double pickupLatitude, Double pickupLongitude,
                              String vehicleType, String vehicleNumber, String licenseNumber,
                              String bankName, String bankAccountName, String bankAccountNumber,
                              BigDecimal commissionPercent, BigDecimal effectiveCommissionPercent,
                              Instant createdAt, Instant reviewedAt, String reviewedBy, Verification verification) {
    }

    /** "Where am I?" for the Sell with us / Deliver with us pages. */
    public record MyApplications(PartnerView seller, PartnerView rider, String role) {
    }

    public record StatusRequest(String status, String note) {
    }

    public record CommissionRequest(BigDecimal commissionPercent) {
    }

    // ---------- Packages ----------

    public record PackageLine(Long itemId, String itemName, String imagePath, Integer quantity, BigDecimal unitPrice) {
    }

    /**
     * One package. Who is looking decides which fields are filled:
     * a rider sees the customer's phone only after taking the job; only the customer sees the delivery code.
     */
    public record PackageView(Long id, Long orderId, String status,
                              Long sellerId, String sellerName, String sellerPhone, String pickupAddress, String pickupTown,
                              String customerName, String customerPhone, String dropAddress,
                              Long riderId, String riderName, String riderPhone, String riderVehicle,
                              BigDecimal itemsSubtotal, BigDecimal commissionPercent, BigDecimal commissionAmount,
                              BigDecimal sellerEarning, BigDecimal deliveryFee, BigDecimal riderPay,
                              String deliverySize, BigDecimal distanceKm, boolean distanceEstimated,
                              Double pickupLatitude, Double pickupLongitude, Double dropLatitude, Double dropLongitude,
                              String deliveryCode, int itemCount, List<PackageLine> items,
                              Instant createdAt, Instant packedAt, Instant assignedAt, Instant pickedUpAt, Instant deliveredAt,
                              boolean selfPickup, String handedOverBy) {
    }

    public record DeliverRequest(String code) {
    }

    // ---------- Money ----------

    /** A seller's or rider's money at a glance. */
    public record EarningsSummary(BigDecimal balanceOwed, BigDecimal totalEarned, BigDecimal totalPaidOut, BigDecimal upcoming,
                                  int deliveredCount, int activeCount) {
    }

    public record LedgerView(Long id, String entryType, BigDecimal amount, Long orderId, Long packageId, String note,
                             Instant createdAt, String createdBy) {
    }

    /** One line of the admin's "who do we owe" list. */
    public record BalanceRow(String partyType, Long partyId, String name, String email, String status,
                             String bankName, String bankAccountName, String bankAccountNumber,
                             BigDecimal balanceOwed, BigDecimal totalEarned, BigDecimal totalPaidOut) {
    }

    public record PayoutRequest(String partyType, Long partyId, BigDecimal amount, String reference) {
    }

    /** A correction with a reason: negative takes money back (e.g. refund for a faulty product), positive adds. */
    public record AdjustmentRequest(String partyType, Long partyId, BigDecimal amount, String reason, Long orderId) {
    }

    /** Admin overview numbers. */
    public record MarketplaceOverview(int pendingApplications, int approvedSellers, int approvedRiders,
                                      int packagesToPack, int packagesReady, int packagesOnTheWay,
                                      BigDecimal commissionEarned, BigDecimal owedToSellers, BigDecimal owedToRiders) {
    }
}
