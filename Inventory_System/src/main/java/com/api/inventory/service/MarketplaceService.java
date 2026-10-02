package com.api.inventory.service;

import com.api.inventory.dto.MarketplaceDTOs.*;
import com.api.inventory.entity.*;
import com.api.inventory.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Sellers and riders joining (apply, approve, reject, suspend), the marketplace settings, and paying people out.
 * The order side (packages, delivery) is in PackageService.
 */
@Service
public class MarketplaceService {

    /** People whose role may be turned into SELLER or RIDER. Staff accounts are never changed by an approval. */
    private static final Set<String> PARTNER_ROLES = Set.of("USER", "SELLER", "RIDER");
    private static final BigDecimal MAX_COMMISSION = new BigDecimal("50");

    private final MarketplaceSettingsRepository settingsRepo;
    private final SellerProfileRepository sellers;
    private final RiderProfileRepository riders;
    private final UserRepository users;
    private final RoleRepository roles;
    private final LedgerEntryRepository ledger;
    private final OrderPackageRepository packages;
    private AuditService audit;
    private LegalTermsService legal;
    private PartnerDocumentService documents;

    @org.springframework.beans.factory.annotation.Autowired
    void setAudit(AuditService audit) {
        this.audit = audit;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setLegal(LegalTermsService legal, PartnerDocumentService documents) {
        this.legal = legal;
        this.documents = documents;
    }

    /** The files sent with an application (any can be empty when re-sending an application that already has them). */
    public record ApplicationFiles(org.springframework.web.multipart.MultipartFile idDocument,
                                   org.springframework.web.multipart.MultipartFile licenceDocument,
                                   org.springframework.web.multipart.MultipartFile businessDocument) {
        public static ApplicationFiles none() {
            return new ApplicationFiles(null, null, null);
        }
    }

    public MarketplaceService(MarketplaceSettingsRepository settingsRepo, SellerProfileRepository sellers, RiderProfileRepository riders,
                              UserRepository users, RoleRepository roles, LedgerEntryRepository ledger, OrderPackageRepository packages) {
        this.settingsRepo = settingsRepo;
        this.sellers = sellers;
        this.riders = riders;
        this.users = users;
        this.roles = roles;
        this.ledger = ledger;
        this.packages = packages;
    }

    // ================= Settings =================

    public MarketplaceSettings settings() {
        return settingsRepo.findById(MarketplaceSettings.ID).orElseGet(() -> settingsRepo.save(new MarketplaceSettings()));
    }

    public SettingsView settingsView() {
        MarketplaceSettings s = settings();
        DeliveryPricingService.Tariff t = DeliveryPricingService.tariffOf(s);
        List<com.api.inventory.dto.DeliveryDTOs.RateView> rates = new ArrayList<>();
        for (DeliverySize size : DeliverySize.values()) {
            rates.add(new com.api.inventory.dto.DeliveryDTOs.RateView(size.name(), size.label, size.vehicle,
                    t.baseFee().get(size), t.perKm().get(size)));
        }
        return new SettingsView(s.getDefaultCommissionPercent(), rates, t.includedKm(), t.riderSharePercent(),
                t.maxDistanceKm(), t.unknownDistanceKm(), s.getShopAddress(), s.getShopLatitude(), s.getShopLongitude(),
                s.getUpdatedAt(), s.getUpdatedBy());
    }

    @Transactional
    public SettingsView updateSettings(SettingsRequest request, String by) {
        MarketplaceSettings s = settings();
        s.setDefaultCommissionPercent(percent(request.defaultCommissionPercent(), "Commission"));

        if (request.rates() != null) {
            for (com.api.inventory.dto.DeliveryDTOs.RateRequest rate : request.rates()) {
                if (rate == null) {
                    continue;
                }
                DeliverySize size = DeliverySize.valueOf(DeliverySize.parse(rate.size()));
                BigDecimal base = money(rate.baseFee(), size.label + " base fee");
                BigDecimal perKm = money(rate.perKm(), size.label + " price per km");
                switch (size) {
                    case SMALL -> { s.setSmallBaseFee(base); s.setSmallPerKm(perKm); }
                    case MEDIUM -> { s.setMediumBaseFee(base); s.setMediumPerKm(perKm); }
                    case LARGE -> { s.setLargeBaseFee(base); s.setLargePerKm(perKm); }
                    case BULKY -> { s.setBulkyBaseFee(base); s.setBulkyPerKm(perKm); }
                }
            }
        }
        if (request.includedKm() != null) {
            s.setIncludedKm(range(request.includedKm(), 0, 50, "Km included in the base fee").setScale(1, RoundingMode.HALF_UP));
        }
        if (request.riderSharePercent() != null) {
            s.setRiderSharePercent(range(request.riderSharePercent(), 1, 100, "The rider's share").setScale(2, RoundingMode.HALF_UP));
        }
        if (request.maxDistanceKm() != null) {
            s.setMaxDistanceKm(range(request.maxDistanceKm(), 1, 500, "The longest delivery").setScale(1, RoundingMode.HALF_UP));
        }
        if (request.unknownDistanceKm() != null) {
            s.setUnknownDistanceKm(range(request.unknownDistanceKm(), 0, 100, "The distance used without a location").setScale(1, RoundingMode.HALF_UP));
        }
        String shop = request.shopAddress() == null ? null : request.shopAddress().trim();
        s.setShopAddress(shop == null || shop.isEmpty() ? null : shop.substring(0, Math.min(300, shop.length())));
        DeliveryPricingService.Point point = DeliveryPricingService.point(request.shopLatitude(), request.shopLongitude());
        s.setShopLatitude(point == null ? null : point.latitude());
        s.setShopLongitude(point == null ? null : point.longitude());

        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(by);
        settingsRepo.save(s);
        DeliveryPricingService.Tariff t = DeliveryPricingService.tariffOf(s);
        audit.record("MARKETPLACE_SETTINGS", "settings", "Commission " + s.getDefaultCommissionPercent() + "%, delivery from Nu. "
                + t.baseFee().get(DeliverySize.SMALL) + " + " + t.perKm().get(DeliverySize.SMALL) + "/km (small), rider share "
                + t.riderSharePercent() + "%");
        return settingsView();
    }

    /** The seller's pickup point on the map. */
    @Transactional
    public PartnerView setPickupLocation(SellerProfile seller, com.api.inventory.dto.DeliveryDTOs.LocationRequest request) {
        DeliveryPricingService.Point point = request == null ? null : DeliveryPricingService.point(request.latitude(), request.longitude());
        if (point == null) {
            throw new IllegalStateException("Send the shop's location (latitude and longitude).");
        }
        seller.setPickupLatitude(point.latitude());
        seller.setPickupLongitude(point.longitude());
        sellers.save(seller);
        audit.record("SELLER_LOCATION", "seller #" + seller.getId(), seller.getShopName() + " set its pickup point");
        return view(seller);
    }

    /** The commission rate that applies to this seller right now. */
    public BigDecimal commissionFor(SellerProfile seller) {
        return seller != null && seller.getCommissionPercent() != null
                ? seller.getCommissionPercent()
                : settings().getDefaultCommissionPercent();
    }

    // ================= Applying =================

    public MyApplications myApplications(String email) {
        User user = user(email);
        PartnerView seller = sellers.findByUserEmail(email).map(this::view).orElse(null);
        PartnerView rider = riders.findByUserEmail(email).map(this::view).orElse(null);
        return new MyApplications(seller, rider, user.getRole().getName());
    }

    @Transactional
    public PartnerView applyAsSeller(String email, SellerApplication form, ApplicationFiles files,
                                     jakarta.servlet.http.HttpServletRequest request) {
        ApplicationFiles f = files == null ? ApplicationFiles.none() : files;
        PartnerDocumentService.check(f.idDocument(), "The CID copy");
        PartnerDocumentService.check(f.businessDocument(), "The trade licence copy");
        requireAgreement(form.acceptedTermsVersion(), form.confirmTrue(), com.api.inventory.entity.LegalTerms.SELLER);
        User user = user(email);
        requirePartnerRole(user);
        riders.findByUserEmail(email).filter(r -> !SellerProfile.REJECTED.equals(r.getStatus())).ifPresent(r -> {
            throw new IllegalStateException("This account is registered as a delivery rider. Use a different account to sell.");
        });

        SellerProfile seller = sellers.findByUserEmail(email).orElseGet(SellerProfile::new);
        if (seller.getId() != null && (seller.isApproved() || SellerProfile.SUSPENDED.equals(seller.getStatus()))) {
            throw new IllegalStateException("You already have a seller account.");
        }

        String cid = cid(form.cidNumber());
        sellers.findByCidNumber(cid).stream().filter(s -> !s.getUser().getEmail().equalsIgnoreCase(email)).findAny().ifPresent(s -> {
            throw new IllegalStateException("This CID number is already registered for another seller account. Please contact us.");
        });
        boolean hasId = seller.getId() != null && documents.has(LedgerEntry.SELLER, seller.getId(), com.api.inventory.entity.PartnerDocument.ID_CARD);
        if (!hasId && (f.idDocument() == null || f.idDocument().isEmpty())) {
            throw new IllegalStateException("Attach a clear photo or scan of the owner's CID card.");
        }

        seller.setUser(user);
        seller.setCidNumber(cid);
        seller.setTradeLicenseNumber(optional(form.tradeLicenseNumber(), 40));
        seller.setTpnNumber(optional(form.tpnNumber(), 30));
        seller.setShopName(required(form.shopName(), "Shop name", 120));
        seller.setPhone(phone(form.phone()));
        seller.setPickupAddress(required(form.pickupAddress(), "Pickup address", 500));
        seller.setTown(required(form.town(), "Town", 80));
        seller.setDescription(optional(form.description(), 500));
        seller.setBankName(required(form.bankName(), "Bank name", 120));
        seller.setBankAccountName(required(form.bankAccountName(), "Account holder name", 120));
        seller.setBankAccountNumber(accountNumber(form.bankAccountNumber()));
        seller.setStatus(SellerProfile.PENDING);   // a refused application can be sent again
        seller.setStatusNote(null);
        SellerProfile saved = sellers.save(seller);

        storeIfSent(LedgerEntry.SELLER, saved.getId(), com.api.inventory.entity.PartnerDocument.ID_CARD, f.idDocument());
        storeIfSent(LedgerEntry.SELLER, saved.getId(), com.api.inventory.entity.PartnerDocument.TRADE_LICENCE, f.businessDocument());
        var accepted = legal.accept(email, com.api.inventory.entity.LegalTerms.SELLER, form.acceptedTermsVersion(), request);
        saved.setTermsVersion(accepted.getVersion());
        saved.setTermsAcceptedAt(accepted.getAcceptedAt());
        return view(sellers.save(saved));
    }

    @Transactional
    public PartnerView applyAsRider(String email, RiderApplication form, ApplicationFiles files,
                                    jakarta.servlet.http.HttpServletRequest request) {
        ApplicationFiles f = files == null ? ApplicationFiles.none() : files;
        PartnerDocumentService.check(f.idDocument(), "The CID copy");
        PartnerDocumentService.check(f.licenceDocument(), "The driving licence copy");
        requireAgreement(form.acceptedTermsVersion(), form.confirmTrue(), com.api.inventory.entity.LegalTerms.RIDER);
        User user = user(email);
        requirePartnerRole(user);
        sellers.findByUserEmail(email).filter(s -> !SellerProfile.REJECTED.equals(s.getStatus())).ifPresent(s -> {
            throw new IllegalStateException("This account is registered as a seller. Use a different account to deliver.");
        });

        RiderProfile rider = riders.findByUserEmail(email).orElseGet(RiderProfile::new);
        if (rider.getId() != null && (rider.isApproved() || SellerProfile.SUSPENDED.equals(rider.getStatus()))) {
            throw new IllegalStateException("You already have a rider account.");
        }

        String cid = cid(form.cidNumber());
        riders.findByCidNumber(cid).stream().filter(r -> !r.getUser().getEmail().equalsIgnoreCase(email)).findAny().ifPresent(r -> {
            throw new IllegalStateException("This CID number is already registered for another driver account. Please contact us.");
        });

        rider.setUser(user);
        rider.setCidNumber(cid);
        rider.setPhone(phone(form.phone()));
        rider.setVehicleType(required(form.vehicleType(), "Vehicle type", 40));
        rider.setVehicleNumber(optional(form.vehicleNumber(), 30));
        if (rider.needsLicence()) {
            rider.setLicenseNumber(required(form.licenseNumber(), "Driving licence number", 40));
            if (form.licenseExpiry() == null) {
                throw new IllegalStateException("Enter the date your driving licence expires.");
            }
            if (!form.licenseExpiry().isAfter(java.time.LocalDate.now())) {
                throw new IllegalStateException("Your driving licence has expired. Renew it before applying.");
            }
            rider.setLicenseExpiry(form.licenseExpiry());
        } else {
            rider.setLicenseNumber(optional(form.licenseNumber(), 40));
            rider.setLicenseExpiry(form.licenseExpiry());
        }
        rider.setEmergencyContactName(required(form.emergencyContactName(), "Emergency contact name", 120));
        String emergency = phone(form.emergencyContactPhone());
        if (emergency.equals(rider.getPhone())) {
            throw new IllegalStateException("The emergency contact needs a different phone number from yours.");
        }
        rider.setEmergencyContactPhone(emergency);

        boolean hasId = rider.getId() != null && documents.has(LedgerEntry.RIDER, rider.getId(), com.api.inventory.entity.PartnerDocument.ID_CARD);
        if (!hasId && (f.idDocument() == null || f.idDocument().isEmpty())) {
            throw new IllegalStateException("Attach a clear photo or scan of your CID card.");
        }
        boolean hasLicence = rider.getId() != null
                && documents.has(LedgerEntry.RIDER, rider.getId(), com.api.inventory.entity.PartnerDocument.DRIVING_LICENCE);
        if (rider.needsLicence() && !hasLicence && (f.licenceDocument() == null || f.licenceDocument().isEmpty())) {
            throw new IllegalStateException("Attach a clear photo or scan of your driving licence.");
        }
        rider.setTown(required(form.town(), "Town", 80));
        rider.setBankName(required(form.bankName(), "Bank name", 120));
        rider.setBankAccountName(required(form.bankAccountName(), "Account holder name", 120));
        rider.setBankAccountNumber(accountNumber(form.bankAccountNumber()));
        rider.setStatus(SellerProfile.PENDING);
        rider.setStatusNote(null);
        RiderProfile saved = riders.save(rider);

        storeIfSent(LedgerEntry.RIDER, saved.getId(), com.api.inventory.entity.PartnerDocument.ID_CARD, f.idDocument());
        storeIfSent(LedgerEntry.RIDER, saved.getId(), com.api.inventory.entity.PartnerDocument.DRIVING_LICENCE, f.licenceDocument());
        var accepted = legal.accept(email, com.api.inventory.entity.LegalTerms.RIDER, form.acceptedTermsVersion(), request);
        saved.setTermsVersion(accepted.getVersion());
        saved.setTermsAcceptedAt(accepted.getAcceptedAt());
        return view(riders.save(saved));
    }

    /** A driver sends a renewed licence (new expiry date and a copy). Works while approved, so deliveries can go on. */
    @Transactional
    public PartnerView renewLicence(String email, java.time.LocalDate expiry, org.springframework.web.multipart.MultipartFile copy) {
        RiderProfile rider = riders.findByUserEmail(email).orElseThrow(() -> new IllegalStateException("You do not have a driver account."));
        PartnerDocumentService.check(copy, "The driving licence copy");
        if (expiry == null || !expiry.isAfter(java.time.LocalDate.now())) {
            throw new IllegalStateException("Enter the new expiry date (a date in the future).");
        }
        if (copy == null || copy.isEmpty()) {
            throw new IllegalStateException("Attach a photo or scan of the renewed licence.");
        }
        rider.setLicenseExpiry(expiry);
        documents.store(LedgerEntry.RIDER, rider.getId(), com.api.inventory.entity.PartnerDocument.DRIVING_LICENCE, copy);
        audit.record("RIDER_LICENCE_RENEWED", "rider " + rider.getId() + " (" + email + ")", "Valid until " + expiry);
        return view(riders.save(rider));
    }

    // ================= Admin: adding a seller or driver directly =================

    /**
     * An admin makes someone a seller (for example a shopkeeper who registered at the counter).
     * The account is approved at once. Documents can be missing (the admin saw them in person), and the
     * person accepts the Seller Agreement themselves the first time they open My shop.
     */
    @Transactional
    public SellerProfile adminCreateSeller(User user, SellerApplication form, String by) {
        riders.findByUserEmail(user.getEmail()).filter(r -> !SellerProfile.REJECTED.equals(r.getStatus())).ifPresent(r -> {
            throw new IllegalStateException("This person is a delivery driver. One account cannot be both.");
        });
        if (sellers.findByUserEmail(user.getEmail()).isPresent()) {
            throw new IllegalStateException("This person already has a seller application. Choose Seller again to approve it.");
        }
        String cid = cid(form.cidNumber());
        if (!sellers.findByCidNumber(cid).isEmpty()) {
            throw new IllegalStateException("This CID number is already registered for another seller.");
        }
        SellerProfile s = new SellerProfile();
        s.setUser(user);
        s.setCidNumber(cid);
        s.setTradeLicenseNumber(optional(form.tradeLicenseNumber(), 40));
        s.setTpnNumber(optional(form.tpnNumber(), 30));
        s.setShopName(required(form.shopName(), "Shop name", 120));
        s.setPhone(phone(form.phone()));
        s.setPickupAddress(required(form.pickupAddress(), "Pickup address", 500));
        s.setTown(required(form.town(), "Town", 80));
        s.setDescription(optional(form.description(), 500));
        s.setBankName(required(form.bankName(), "Bank name", 120));
        s.setBankAccountName(required(form.bankAccountName(), "Account holder name", 120));
        s.setBankAccountNumber(accountNumber(form.bankAccountNumber()));
        s.setStatus(SellerProfile.APPROVED);
        s.setStatusNote("Added by an administrator.");
        s.setReviewedAt(Instant.now());
        s.setReviewedBy(by);
        SellerProfile saved = sellers.save(s);
        audit.record("SELLER_APPROVED", "seller " + saved.getId() + " (" + saved.getShopName() + ")", "Added directly by an administrator");
        return saved;
    }

    /** An admin makes someone a delivery driver. Same idea as adminCreateSeller. */
    @Transactional
    public RiderProfile adminCreateRider(User user, RiderApplication form, String by) {
        sellers.findByUserEmail(user.getEmail()).filter(s -> !SellerProfile.REJECTED.equals(s.getStatus())).ifPresent(s -> {
            throw new IllegalStateException("This person is a seller. One account cannot be both.");
        });
        if (riders.findByUserEmail(user.getEmail()).isPresent()) {
            throw new IllegalStateException("This person already has a driver application. Choose Delivery driver again to approve it.");
        }
        String cid = cid(form.cidNumber());
        if (!riders.findByCidNumber(cid).isEmpty()) {
            throw new IllegalStateException("This CID number is already registered for another driver.");
        }
        RiderProfile r = new RiderProfile();
        r.setUser(user);
        r.setCidNumber(cid);
        r.setPhone(phone(form.phone()));
        r.setVehicleType(required(form.vehicleType(), "Vehicle type", 40));
        r.setVehicleNumber(optional(form.vehicleNumber(), 30));
        r.setTown(required(form.town(), "Town", 80));
        if (r.needsLicence()) {
            r.setLicenseNumber(required(form.licenseNumber(), "Driving licence number", 40));
            if (form.licenseExpiry() == null || !form.licenseExpiry().isAfter(java.time.LocalDate.now())) {
                throw new IllegalStateException("Enter a driving licence expiry date in the future.");
            }
            r.setLicenseExpiry(form.licenseExpiry());
        } else {
            r.setLicenseNumber(optional(form.licenseNumber(), 40));
        }
        r.setEmergencyContactName(required(form.emergencyContactName(), "Emergency contact name", 120));
        r.setEmergencyContactPhone(phone(form.emergencyContactPhone()));
        r.setBankName(required(form.bankName(), "Bank name", 120));
        r.setBankAccountName(required(form.bankAccountName(), "Account holder name", 120));
        r.setBankAccountNumber(accountNumber(form.bankAccountNumber()));
        r.setStatus(SellerProfile.APPROVED);
        r.setStatusNote("Added by an administrator.");
        r.setReviewedAt(Instant.now());
        r.setReviewedBy(by);
        RiderProfile saved = riders.save(r);
        audit.record("RIDER_APPROVED", "rider " + saved.getId() + " (" + user.getEmail() + ")", "Added directly by an administrator");
        return saved;
    }

    /** Approves this person's existing seller or driver application (used when an admin picks the role in People). */
    @Transactional
    public void approveExisting(User user, String partnerRole, String by) {
        if ("SELLER".equals(partnerRole)) {
            SellerProfile s = sellers.findByUserEmail(user.getEmail())
                    .orElseThrow(() -> new IllegalStateException("Fill in the seller's shop details first."));
            if (!s.isApproved()) {
                setSellerStatus(s.getId(), new StatusRequest(SellerProfile.APPROVED, null), by);
            }
        } else {
            RiderProfile r = riders.findByUserEmail(user.getEmail())
                    .orElseThrow(() -> new IllegalStateException("Fill in the driver's details first."));
            if (!r.isApproved()) {
                setRiderStatus(r.getId(), new StatusRequest(SellerProfile.APPROVED, null), by);
            }
        }
    }

    // ================= Admin: review =================

    public List<PartnerView> allPartners() {
        List<PartnerView> all = new ArrayList<>();
        sellers.findAllByOrderByCreatedAtDesc().forEach(s -> all.add(view(s)));
        riders.findAllByOrderByCreatedAtDesc().forEach(r -> all.add(view(r)));
        all.sort((a, b) -> b.createdAt() == null || a.createdAt() == null ? 0 : b.createdAt().compareTo(a.createdAt()));
        return all;
    }

    /** APPROVED gives the person the SELLER role, SUSPENDED takes it away, REJECTED just closes the application. */
    @Transactional
    public PartnerView setSellerStatus(Long sellerId, StatusRequest request, String by) {
        SellerProfile seller = sellers.findById(sellerId).orElseThrow(() -> new IllegalStateException("Seller not found."));
        String status = reviewStatus(request);
        seller.setStatus(status);
        seller.setStatusNote(optional(request.note(), 500));
        seller.setReviewedAt(Instant.now());
        seller.setReviewedBy(by);
        changeRole(seller.getUser(), status, "SELLER");
        audit.record("SELLER_" + status, "seller " + seller.getId() + " (" + seller.getShopName() + ")", seller.getStatusNote());
        return view(sellers.save(seller));
    }

    @Transactional
    public PartnerView setRiderStatus(Long riderId, StatusRequest request, String by) {
        RiderProfile rider = riders.findById(riderId).orElseThrow(() -> new IllegalStateException("Rider not found."));
        String status = reviewStatus(request);
        if (SellerProfile.APPROVED.equals(status) && rider.licenceExpired()) {
            throw new IllegalStateException("This driver's licence expired on " + rider.getLicenseExpiry()
                    + ". Ask them to send the renewed licence first.");
        }
        if (!SellerProfile.APPROVED.equals(status)) {
            boolean busy = packages.findByRiderIdOrderByIdDesc(riderId).stream()
                    .anyMatch(p -> OrderPackage.ASSIGNED.equals(p.getStatus()) || OrderPackage.PICKED_UP.equals(p.getStatus()));
            if (busy) {
                throw new IllegalStateException("This rider is carrying a package right now. Wait until it is delivered.");
            }
        }
        rider.setStatus(status);
        rider.setStatusNote(optional(request.note(), 500));
        rider.setReviewedAt(Instant.now());
        rider.setReviewedBy(by);
        changeRole(rider.getUser(), status, "RIDER");
        audit.record("RIDER_" + status, "rider " + rider.getId() + " (" + rider.getUser().getEmail() + ")", rider.getStatusNote());
        return view(riders.save(rider));
    }

    /** Empty rate = use the default from the settings. */
    @Transactional
    public PartnerView setSellerCommission(Long sellerId, CommissionRequest request) {
        SellerProfile seller = sellers.findById(sellerId).orElseThrow(() -> new IllegalStateException("Seller not found."));
        seller.setCommissionPercent(request.commissionPercent() == null ? null : percent(request.commissionPercent(), "Commission"));
        audit.record("SELLER_COMMISSION", "seller " + seller.getId() + " (" + seller.getShopName() + ")", request.commissionPercent() == null ? "default" : seller.getCommissionPercent() + "%");
        return view(sellers.save(seller));
    }

    // ================= Money =================

    public EarningsSummary earnings(String partyType, Long partyId, List<OrderPackage> theirPackages) {
        BigDecimal earnedType = LedgerEntry.SELLER.equals(partyType) ? ledger.sumOf(partyType, partyId, LedgerEntry.SALE)
                : ledger.sumOf(partyType, partyId, LedgerEntry.DELIVERY);
        BigDecimal paid = ledger.sumOf(partyType, partyId, LedgerEntry.PAYOUT).negate();
        BigDecimal balance = ledger.balanceOf(partyType, partyId);

        BigDecimal upcoming = BigDecimal.ZERO;
        int delivered = 0;
        int active = 0;
        for (OrderPackage p : theirPackages) {
            String st = p.getStatus();
            if (OrderPackage.DELIVERED.equals(st)) {
                delivered++;
            } else if (!OrderPackage.CANCELLED.equals(st) && !OrderPackage.PENDING_PAYMENT.equals(st)) {
                active++;
                upcoming = upcoming.add(LedgerEntry.SELLER.equals(partyType) ? p.getSellerEarning() : p.getRiderPay());
            }
        }
        return new EarningsSummary(balance, earnedType, paid, upcoming, delivered, active);
    }

    public List<LedgerView> ledgerFor(String partyType, Long partyId) {
        return ledger.findByPartyTypeAndPartyIdOrderByIdDesc(partyType, partyId).stream()
                .map(e -> new LedgerView(e.getId(), e.getEntryType(), e.getAmount(), e.getOrderId(), e.getPackageId(), e.getNote(),
                        e.getCreatedAt(), e.getCreatedBy()))
                .toList();
    }

    public List<BalanceRow> balances() {
        List<BalanceRow> rows = new ArrayList<>();
        for (SellerProfile s : sellers.findAllByOrderByCreatedAtDesc()) {
            if (SellerProfile.PENDING.equals(s.getStatus()) || SellerProfile.REJECTED.equals(s.getStatus())) {
                continue;
            }
            rows.add(new BalanceRow(LedgerEntry.SELLER, s.getId(), s.getShopName(), s.getUser().getEmail(), s.getStatus(),
                    s.getBankName(), s.getBankAccountName(), s.getBankAccountNumber(),
                    ledger.balanceOf(LedgerEntry.SELLER, s.getId()), ledger.sumOf(LedgerEntry.SELLER, s.getId(), LedgerEntry.SALE),
                    ledger.sumOf(LedgerEntry.SELLER, s.getId(), LedgerEntry.PAYOUT).negate()));
        }
        for (RiderProfile r : riders.findAllByOrderByCreatedAtDesc()) {
            if (SellerProfile.PENDING.equals(r.getStatus()) || SellerProfile.REJECTED.equals(r.getStatus())) {
                continue;
            }
            rows.add(new BalanceRow(LedgerEntry.RIDER, r.getId(), r.getUser().getName(), r.getUser().getEmail(), r.getStatus(),
                    r.getBankName(), r.getBankAccountName(), r.getBankAccountNumber(),
                    ledger.balanceOf(LedgerEntry.RIDER, r.getId()), ledger.sumOf(LedgerEntry.RIDER, r.getId(), LedgerEntry.DELIVERY),
                    ledger.sumOf(LedgerEntry.RIDER, r.getId(), LedgerEntry.PAYOUT).negate()));
        }
        return rows;
    }

    /** We sent money to a seller or rider. Never more than we owe them. */
    @Transactional
    public LedgerView recordPayout(PayoutRequest request, String by) {
        String type = request.partyType() == null ? "" : request.partyType().trim().toUpperCase();
        if (!LedgerEntry.SELLER.equals(type) && !LedgerEntry.RIDER.equals(type)) {
            throw new IllegalStateException("Choose a seller or a rider.");
        }
        boolean exists = LedgerEntry.SELLER.equals(type) ? sellers.existsById(request.partyId()) : riders.existsById(request.partyId());
        if (request.partyId() == null || !exists) {
            throw new IllegalStateException("That seller or rider was not found.");
        }
        BigDecimal amount = money(request.amount(), "Amount");
        if (amount.signum() <= 0) {
            throw new IllegalStateException("Enter the amount you paid.");
        }
        BigDecimal owed = ledger.balanceOf(type, request.partyId());
        if (amount.compareTo(owed) > 0) {
            throw new IllegalStateException("That is more than we owe (Nu. " + owed.setScale(2, RoundingMode.HALF_UP) + ").");
        }
        LedgerEntry entry = new LedgerEntry();
        entry.setPartyType(type);
        entry.setPartyId(request.partyId());
        entry.setEntryType(LedgerEntry.PAYOUT);
        entry.setAmount(amount.negate());
        entry.setNote(required(request.reference(), "Bank reference (journal number)", 300));
        entry.setCreatedBy(by);
        LedgerEntry saved = ledger.save(entry);
        audit.record("PAYOUT", type + " " + request.partyId(), "Nu. " + amount + ", reference " + entry.getNote());
        return new LedgerView(saved.getId(), saved.getEntryType(), saved.getAmount(), null, null, saved.getNote(), saved.getCreatedAt(), by);
    }

    /** A correction to what we owe a seller or driver, always with a reason. Recorded, never edited. */
    @Transactional
    public LedgerView recordAdjustment(AdjustmentRequest request, String by) {
        String type = request.partyType() == null ? "" : request.partyType().trim().toUpperCase();
        boolean exists = LedgerEntry.SELLER.equals(type) ? request.partyId() != null && sellers.existsById(request.partyId())
                : LedgerEntry.RIDER.equals(type) && request.partyId() != null && riders.existsById(request.partyId());
        if (!exists) {
            throw new IllegalStateException("That seller or driver was not found.");
        }
        if (request.amount() == null || request.amount().signum() == 0) {
            throw new IllegalStateException("Enter the amount (minus to take money back, plus to add).");
        }
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.abs().compareTo(new BigDecimal("1000000")) > 0) {
            throw new IllegalStateException("That amount is too large.");
        }
        LedgerEntry entry = new LedgerEntry();
        entry.setPartyType(type);
        entry.setPartyId(request.partyId());
        entry.setEntryType(LedgerEntry.ADJUSTMENT);
        entry.setAmount(amount);
        entry.setOrderId(request.orderId());
        entry.setNote(required(request.reason(), "Reason", 300));
        entry.setCreatedBy(by);
        LedgerEntry saved = ledger.save(entry);
        audit.record("LEDGER_ADJUSTMENT", type + " " + request.partyId(), "Nu. " + amount + ": " + entry.getNote());
        return new LedgerView(saved.getId(), saved.getEntryType(), saved.getAmount(), saved.getOrderId(), null, saved.getNote(), saved.getCreatedAt(), by);
    }

    public MarketplaceOverview overview() {
        int pending = (int) (sellers.findAll().stream().filter(s -> SellerProfile.PENDING.equals(s.getStatus())).count()
                + riders.findAll().stream().filter(r -> SellerProfile.PENDING.equals(r.getStatus())).count());
        int approvedSellers = (int) sellers.findAll().stream().filter(SellerProfile::isApproved).count();
        int approvedRiders = (int) riders.findAll().stream().filter(RiderProfile::isApproved).count();

        int toPack = 0, ready = 0, onTheWay = 0;
        BigDecimal commission = BigDecimal.ZERO;
        for (OrderPackage p : packages.findAll()) {
            switch (p.getStatus()) {
                case OrderPackage.TO_PACK -> toPack++;
                case OrderPackage.READY_FOR_PICKUP -> ready++;
                case OrderPackage.ASSIGNED, OrderPackage.PICKED_UP -> onTheWay++;
                case OrderPackage.DELIVERED -> commission = commission.add(p.getCommissionAmount());
                default -> { }
            }
        }
        BigDecimal owedSellers = BigDecimal.ZERO;
        BigDecimal owedRiders = BigDecimal.ZERO;
        for (BalanceRow row : balances()) {
            if (LedgerEntry.SELLER.equals(row.partyType())) {
                owedSellers = owedSellers.add(row.balanceOwed());
            } else {
                owedRiders = owedRiders.add(row.balanceOwed());
            }
        }
        return new MarketplaceOverview(pending, approvedSellers, approvedRiders, toPack, ready, onTheWay, commission, owedSellers, owedRiders);
    }

    // ================= Views =================

    public PartnerView view(SellerProfile s) {
        User u = s.getUser();
        return new PartnerView(s.getId(), "SELLER", s.getStatus(), s.getStatusNote(), u.getName(), u.getEmail(), s.getPhone(),
                s.getShopName(), s.getPickupAddress(), s.getTown(), s.getDescription(),
                s.getPickupLatitude(), s.getPickupLongitude(), null, null, null,
                s.getBankName(), s.getBankAccountName(), s.getBankAccountNumber(),
                s.getCommissionPercent(), commissionFor(s), s.getCreatedAt(), s.getReviewedAt(), s.getReviewedBy(),
                new Verification(s.getCidNumber(), s.getTradeLicenseNumber(), s.getTpnNumber(), null, false, null, null,
                        s.getTermsVersion(), s.getTermsAcceptedAt(),
                        documents == null ? List.of() : documents.list(LedgerEntry.SELLER, s.getId())));
    }

    public PartnerView view(RiderProfile r) {
        User u = r.getUser();
        return new PartnerView(r.getId(), "RIDER", r.getStatus(), r.getStatusNote(), u.getName(), u.getEmail(), r.getPhone(),
                null, null, r.getTown(), null, null, null, r.getVehicleType(), r.getVehicleNumber(), r.getLicenseNumber(),
                r.getBankName(), r.getBankAccountName(), r.getBankAccountNumber(),
                null, null, r.getCreatedAt(), r.getReviewedAt(), r.getReviewedBy(),
                new Verification(r.getCidNumber(), null, null, r.getLicenseExpiry(), r.licenceExpired(),
                        r.getEmergencyContactName(), r.getEmergencyContactPhone(), r.getTermsVersion(), r.getTermsAcceptedAt(),
                        documents == null ? List.of() : documents.list(LedgerEntry.RIDER, r.getId())));
    }

    // ================= Helpers =================

    private void changeRole(User user, String status, String partnerRole) {
        String current = user.getRole() == null ? "" : user.getRole().getName().toUpperCase();
        if (!PARTNER_ROLES.contains(current)) {
            return; // never demote or change a staff account
        }
        String wanted = SellerProfile.APPROVED.equals(status) ? partnerRole : "USER";
        if (!wanted.equals(current)) {
            user.setRole(roles.findByName(wanted).orElseThrow(() -> new IllegalStateException("Role " + wanted + " is missing.")));
            users.save(user);
        }
    }

    private static String reviewStatus(StatusRequest request) {
        String status = request == null || request.status() == null ? "" : request.status().trim().toUpperCase();
        if (!Set.of(SellerProfile.APPROVED, SellerProfile.REJECTED, SellerProfile.SUSPENDED).contains(status)) {
            throw new IllegalStateException("Choose approve, reject or suspend.");
        }
        if (!SellerProfile.APPROVED.equals(status) && (request.note() == null || request.note().isBlank())) {
            throw new IllegalStateException("Please give a short reason. The person will see it.");
        }
        return status;
    }

    private void requirePartnerRole(User user) {
        String role = user.getRole() == null ? "" : user.getRole().getName().toUpperCase();
        if (!PARTNER_ROLES.contains(role)) {
            throw new IllegalStateException("Staff accounts cannot apply. Please use a customer account.");
        }
    }

    private User user(String email) {
        return users.findByEmail(email).orElseThrow(() -> new IllegalStateException("Please sign in again."));
    }

    private static String required(String value, String label, int max) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            throw new IllegalStateException(label + " is required.");
        }
        if (v.length() > max) {
            throw new IllegalStateException(label + " is too long (at most " + max + " characters).");
        }
        return v;
    }

    private static String optional(String value, int max) {
        String v = value == null ? "" : value.trim();
        return v.isEmpty() ? null : v.substring(0, Math.min(max, v.length()));
    }

    private void requireAgreement(Integer version, Boolean confirmTrue, String type) {
        if (!Boolean.TRUE.equals(confirmTrue)) {
            throw new IllegalStateException("Please confirm that the information you gave is true.");
        }
        if (version == null) {
            throw new IllegalStateException("Please read and accept the " + legal.current(type).getTitle() + ".");
        }
        Integer current = legal.current(type).getVersion();
        if (!version.equals(current)) {
            throw new IllegalStateException("The agreement was updated while you were filling in the form. Please read version "
                    + current + " and accept it again.");
        }
    }

    private void storeIfSent(String partnerType, Long partnerId, String kind, org.springframework.web.multipart.MultipartFile file) {
        if (file != null && !file.isEmpty()) {
            documents.store(partnerType, partnerId, kind, file);
        }
    }

    /** Bhutanese CID numbers have 11 digits. */
    private static String cid(String value) {
        String v = value == null ? "" : value.replaceAll("\\s", "");
        if (!v.matches("^[0-9]{11}$")) {
            throw new IllegalStateException("Enter the 11-digit CID number.");
        }
        return v;
    }

    private static String phone(String value) {
        String v = value == null ? "" : value.trim();
        if (!v.matches("^[0-9]{8}$")) {
            throw new IllegalStateException("Enter an 8-digit phone number.");
        }
        return v;
    }

    private static String accountNumber(String value) {
        String v = value == null ? "" : value.replaceAll("\\s", "");
        if (!v.matches("^[0-9]{6,20}$")) {
            throw new IllegalStateException("Enter the bank account number (digits only).");
        }
        return v;
    }

    private static BigDecimal percent(BigDecimal value, String label) {
        if (value == null || value.signum() < 0 || value.compareTo(MAX_COMMISSION) > 0) {
            throw new IllegalStateException(label + " must be between 0 and " + MAX_COMMISSION + " percent.");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal range(BigDecimal value, int min, int max, String label) {
        if (value.compareTo(BigDecimal.valueOf(min)) < 0 || value.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw new IllegalStateException(label + " must be between " + min + " and " + max + ".");
        }
        return value;
    }

    private static BigDecimal money(BigDecimal value, String label) {
        if (value == null || value.signum() < 0) {
            throw new IllegalStateException(label + " cannot be empty or negative.");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
