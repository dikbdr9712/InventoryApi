package com.api.inventory.service;

import com.api.inventory.dto.MarketplaceDTOs.*;
import com.api.inventory.entity.*;
import com.api.inventory.repository.*;
import com.api.inventory.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/**
 * The life of a package, from "the customer paid" to "the customer has it":
 *
 *   PENDING_PAYMENT  order placed, waiting for the payment to be verified
 *   TO_PACK          paid: the seller (or our staff, for our own products) gets it ready
 *   READY_FOR_PICKUP packed: every approved rider can see the job
 *   ASSIGNED         a rider took the job and is on the way to the seller
 *   PICKED_UP        the rider has it (the order shows "On the way")
 *   DELIVERED        the rider entered the customer's 4-digit code. The seller and rider earnings are booked.
 *
 * Staff with orders.fulfil can do any step themselves (for example deliver with our own vehicle).
 */
@Service
public class PackageService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> RIDER_ACTIVE = Set.of(OrderPackage.ASSIGNED, OrderPackage.PICKED_UP);
    /** A rider can carry at most this many jobs at once, so jobs are shared fairly. */
    private static final int MAX_ACTIVE_JOBS_PER_RIDER = 5;

    private final OrderPackageRepository packages;
    private final OrderItemRepository orderItems;
    private final OrderRepository orders;
    private final ItemMasterRepository itemsRepo;
    private final SellerProfileRepository sellers;
    private final RiderProfileRepository riders;
    private final LedgerEntryRepository ledger;
    private final MarketplaceService marketplace;
    private final DeliveryPricingService pricing;
    private LegalTermsService legal;

    @org.springframework.beans.factory.annotation.Autowired
    void setLegal(LegalTermsService legal) {
        this.legal = legal;
    }

    private NotificationService notify;

    @org.springframework.beans.factory.annotation.Autowired
    void setNotify(NotificationService notify) {
        this.notify = notify;
    }

    public PackageService(OrderPackageRepository packages, OrderItemRepository orderItems, OrderRepository orders,
                          ItemMasterRepository itemsRepo, SellerProfileRepository sellers, RiderProfileRepository riders,
                          LedgerEntryRepository ledger, MarketplaceService marketplace, DeliveryPricingService pricing) {
        this.packages = packages;
        this.orderItems = orderItems;
        this.orders = orders;
        this.itemsRepo = itemsRepo;
        this.sellers = sellers;
        this.riders = riders;
        this.ledger = ledger;
        this.marketplace = marketplace;
        this.pricing = pricing;
    }

    // ================= Creating (at checkout) =================

    /**
     * Splits the order's lines by seller: one package each. Prices each delivery by size and distance
     * (DeliveryPricingService) and freezes the commission, the delivery fee and the rider pay.
     * The lines must already be priced by the server. Returns the total delivery fee to add to the order.
     */
    @Transactional
    public BigDecimal createPackages(Order order, List<OrderItem> lines) {
        DeliveryPricingService.Point drop = order.getDropLatitude() == null || order.getDropLongitude() == null
                ? null : new DeliveryPricingService.Point(order.getDropLatitude(), order.getDropLongitude());

        Map<Long, List<OrderItem>> bySeller = new LinkedHashMap<>();   // a null key = our own shop
        Map<Long, ItemMaster> products = new HashMap<>();
        for (OrderItem line : lines) {
            ItemMaster product = products.computeIfAbsent(line.getItemId(), id -> itemsRepo.findById(id).orElse(null));
            Long sellerId = product == null ? null : product.getSellerId();
            bySeller.computeIfAbsent(sellerId, k -> new ArrayList<>()).add(line);
        }

        BigDecimal totalFees = BigDecimal.ZERO;
        for (Map.Entry<Long, List<OrderItem>> group : bySeller.entrySet()) {
            SellerProfile seller = group.getKey() == null ? null : sellers.findById(group.getKey()).orElse(null);

            BigDecimal subtotal = BigDecimal.ZERO;
            List<ItemMaster> groupProducts = new ArrayList<>();
            for (OrderItem line : group.getValue()) {
                subtotal = subtotal.add(line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
                ItemMaster product = products.get(line.getItemId());
                if (product != null) {
                    groupProducts.add(product);
                }
            }
            subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
            DeliveryPricingService.PlannedPackage planned = pricing.plan(seller, groupProducts, drop);
            DeliveryPricingService.Price price = planned.price();

            OrderPackage pkg = new OrderPackage();
            pkg.setOrderId(order.getOrderId());
            pkg.setSellerId(seller == null ? null : seller.getId());
            pkg.setItemsSubtotal(subtotal);
            if (seller != null) {
                BigDecimal rate = marketplace.commissionFor(seller);
                BigDecimal commission = subtotal.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                pkg.setCommissionPercent(rate);
                pkg.setCommissionAmount(commission);
                pkg.setSellerEarning(subtotal.subtract(commission));
            }
            pkg.setPickupAddress(planned.pickupAddress());
            pkg.setDeliverySize(price.size().name());
            pkg.setDistanceKm(price.distanceKm());
            pkg.setDistanceEstimated(price.estimated());
            pkg.setDeliveryFee(price.fee());
            pkg.setRiderPay(price.riderPay());
            if (planned.pickup() != null) {
                pkg.setPickupLatitude(planned.pickup().latitude());
                pkg.setPickupLongitude(planned.pickup().longitude());
            }
            if (drop != null) {
                pkg.setDropLatitude(drop.latitude());
                pkg.setDropLongitude(drop.longitude());
            }
            pkg.setDropAddress(order.getAddress());
            pkg.setDeliveryCode(String.format("%04d", RANDOM.nextInt(10_000)));
            pkg.setStatus(OrderPackage.PENDING_PAYMENT);
            OrderPackage saved = packages.save(pkg);

            for (OrderItem line : group.getValue()) {
                line.setPackageId(saved.getId());
            }
            totalFees = totalFees.add(price.fee());
        }
        return totalFees;
    }

    public boolean hasPackages(Long orderId) {
        return !packages.findByOrderIdOrderByIdAsc(orderId).isEmpty();
    }

    // ================= Order events =================

    /** The order's payment was verified: the sellers can start packing. Safe to call twice. */
    @Transactional
    public void markOrderPaid(Long orderId) {
        List<OrderPackage> list = packages.findByOrderIdOrderByIdAsc(orderId);
        boolean changed = false;
        for (OrderPackage p : list) {
            if (OrderPackage.PENDING_PAYMENT.equals(p.getStatus())) {
                p.setStatus(OrderPackage.TO_PACK);
                packages.save(p);
                changed = true;
                NotificationService.Note toPack = new NotificationService.Note("NEW_ORDER", "New order to pack: #" + orderId,
                        "Items worth Nu. " + p.getItemsSubtotal() + ". Pack it and press Packed, a rider will collect it.",
                        p.getSellerId() == null ? "/admin/deliveries" : "/seller");
                if (p.getSellerId() == null) {
                    notify.withPermission("orders.fulfil", toPack, false);
                } else {
                    notify.seller(p.getSellerId(), toPack, true);
                }
            }
        }
        if (changed || list.isEmpty()) {
            orders.findById(orderId).ifPresent(order -> notify.customer(order, new NotificationService.Note("ORDER_PAID",
                    "Payment received: order #" + orderId + " is confirmed",
                    "Thank you. We are getting your order ready and will tell you when it is on the way.", "/orders/" + orderId), true, null));
        }
    }

    /** The order was cancelled: packages that have not left the seller are cancelled too. */
    @Transactional
    public void cancelForOrder(Long orderId) {
        for (OrderPackage p : packages.findByOrderIdOrderByIdAsc(orderId)) {
            if (OrderPackage.PICKED_UP.equals(p.getStatus()) || OrderPackage.DELIVERED.equals(p.getStatus())) {
                throw new IllegalStateException("Part of this order is already on the way or delivered. Use Return instead.");
            }
        }
        for (OrderPackage p : packages.findByOrderIdOrderByIdAsc(orderId)) {
            if (!OrderPackage.CANCELLED.equals(p.getStatus())) {
                boolean wasPaid = !OrderPackage.PENDING_PAYMENT.equals(p.getStatus());
                p.setStatus(OrderPackage.CANCELLED);
                p.setCancelledAt(Instant.now());
                p.setUpdatedBy(CurrentUser.email());
                packages.save(p);
                if (wasPaid) {
                    NotificationService.Note stop = new NotificationService.Note("ORDER_CANCELLED", "Order #" + orderId + " was cancelled",
                            "Do not pack or hand over this package.", p.getSellerId() == null ? "/admin/deliveries" : "/seller");
                    notify.seller(p.getSellerId(), stop, true);
                    notify.rider(p.getRiderId(), stop, false);
                }
            }
        }
    }

    // ================= Seller =================

    public SellerProfile requireApprovedSeller() {
        SellerProfile seller = sellers.findByUserEmail(CurrentUser.email())
                .orElseThrow(() -> new AccessDeniedException("You do not have a seller account."));
        if (!seller.isApproved()) {
            throw new AccessDeniedException("Your seller account is not active.");
        }
        // a new version of the Seller Agreement must be accepted before selling goes on
        legal.requireAcceptedCurrent(seller.getUser().getEmail(), com.api.inventory.entity.LegalTerms.SELLER);
        return seller;
    }

    public List<PackageView> sellerPackages(SellerProfile seller) {
        return packages.findBySellerIdOrderByIdDesc(seller.getId()).stream()
                .filter(p -> !OrderPackage.PENDING_PAYMENT.equals(p.getStatus()))   // nothing to do until it is paid
                .map(p -> view(p, Audience.SELLER))
                .toList();
    }

    /** The seller (or staff) says the package is packed and can be collected. */
    @Transactional
    public PackageView markPacked(Long packageId) {
        OrderPackage p = packages.findByIdForUpdate(packageId).orElseThrow(() -> new IllegalStateException("Package not found."));
        boolean staff = CurrentUser.has("orders.fulfil");
        if (!staff) {
            SellerProfile seller = requireApprovedSeller();
            if (!Objects.equals(seller.getId(), p.getSellerId())) {
                throw new AccessDeniedException("This is not your package.");
            }
        }
        requireStatus(p, OrderPackage.TO_PACK, "Only a paid package that is waiting to be packed can be marked as packed.");
        p.setStatus(OrderPackage.READY_FOR_PICKUP);
        p.setPackedAt(Instant.now());
        p.setUpdatedBy(CurrentUser.email());
        OrderPackage saved = packages.save(p);

        DeliverySize size = DeliverySize.of(saved.getDeliverySize());
        SellerProfile from = saved.getSellerId() == null ? null : sellers.findById(saved.getSellerId()).orElse(null);
        notify.ridersWhoCanCarry(size, new NotificationService.Note("NEW_JOB", "New delivery job: Nu. " + saved.getRiderPay(),
                "Collect from " + (from == null ? "DK/Phar" : from.getShopName() + (from.getTown() == null ? "" : ", " + from.getTown()))
                        + " · " + size.label + (saved.getDistanceKm() == null ? "" : " · " + saved.getDistanceKm().stripTrailingZeros().toPlainString() + " km")
                        + ". First come, first served.", "/rider"));
        return view(saved, staff ? Audience.STAFF : Audience.SELLER);
    }

    // ================= Rider =================

    public RiderProfile requireApprovedRider() {
        RiderProfile rider = riders.findByUserEmail(CurrentUser.email())
                .orElseThrow(() -> new AccessDeniedException("You do not have a rider account."));
        if (!rider.isApproved()) {
            throw new AccessDeniedException("Your driver account is not active.");
        }
        legal.requireAcceptedCurrent(rider.getUser().getEmail(), com.api.inventory.entity.LegalTerms.RIDER);
        return rider;
    }

    /**
     * Jobs this rider may take: ready for pickup, and small enough for their vehicle (a washing machine never
     * shows up for a rider on a scooter). The customer's name, phone and exact drop point stay hidden until
     * a rider takes the job.
     */
    public List<PackageView> openJobs(RiderProfile rider) {
        return packages.findByStatusOrderByIdAsc(OrderPackage.READY_FOR_PICKUP).stream()
                .filter(p -> DeliverySize.of(p.getDeliverySize()).fits(rider.getVehicleType()))
                .map(p -> view(p, Audience.RIDER_BOARD))
                .toList();
    }

    public List<PackageView> riderJobs(RiderProfile rider) {
        return packages.findByRiderIdOrderByIdDesc(rider.getId()).stream().map(p -> view(p, Audience.RIDER)).toList();
    }

    @Transactional
    public PackageView accept(Long packageId) {
        RiderProfile rider = requireApprovedRider();
        if (rider.licenceExpired()) {
            throw new IllegalStateException("Your driving licence expired on " + rider.getLicenseExpiry()
                    + ". Send us your renewed licence (My deliveries, top of the page) to take jobs again.");
        }
        long active = packages.findByRiderIdOrderByIdDesc(rider.getId()).stream().filter(p -> RIDER_ACTIVE.contains(p.getStatus())).count();
        if (active >= MAX_ACTIVE_JOBS_PER_RIDER) {
            throw new IllegalStateException("You already have " + MAX_ACTIVE_JOBS_PER_RIDER + " jobs. Deliver one before taking another.");
        }
        OrderPackage p = packages.findByIdForUpdate(packageId).orElseThrow(() -> new IllegalStateException("Job not found."));
        if (!OrderPackage.READY_FOR_PICKUP.equals(p.getStatus()) || p.getRiderId() != null) {
            throw new IllegalStateException("Sorry, another rider has already taken this job.");
        }
        DeliverySize size = DeliverySize.of(p.getDeliverySize());
        if (!size.fits(rider.getVehicleType())) {
            throw new IllegalStateException("This package is " + size.label.toLowerCase() + " (" + size.vehicle.toLowerCase()
                    + "). Your " + rider.getVehicleType().toLowerCase() + " cannot carry it.");
        }
        p.setRiderId(rider.getId());
        p.setStatus(OrderPackage.ASSIGNED);
        p.setAssignedAt(Instant.now());
        p.setUpdatedBy(CurrentUser.email());
        OrderPackage saved = packages.save(p);

        String riderName = rider.getUser().getName();
        orders.findById(saved.getOrderId()).ifPresent(order -> notify.customer(order, new NotificationService.Note("RIDER_ASSIGNED",
                "A rider is collecting your order #" + saved.getOrderId(),
                riderName + " (" + rider.getVehicleType() + ") is on the way to the shop.", "/orders/" + saved.getOrderId()), false, null));
        notify.seller(saved.getSellerId(), new NotificationService.Note("RIDER_ASSIGNED", "Rider coming for order #" + saved.getOrderId(),
                riderName + " will collect the package" + (rider.getPhone() == null ? "." : ". Phone: " + rider.getPhone()), "/seller"), false);
        return view(saved, Audience.RIDER);
    }

    /** The rider cannot do the job after all: it goes back on the board. */
    @Transactional
    public PackageView release(Long packageId) {
        RiderProfile rider = requireApprovedRider();
        OrderPackage p = packages.findByIdForUpdate(packageId).orElseThrow(() -> new IllegalStateException("Job not found."));
        if (!Objects.equals(rider.getId(), p.getRiderId())) {
            throw new AccessDeniedException("This is not your job.");
        }
        requireStatus(p, OrderPackage.ASSIGNED, "You can only give back a job before you pick it up.");
        p.setRiderId(null);
        p.setAssignedAt(null);
        p.setStatus(OrderPackage.READY_FOR_PICKUP);
        p.setUpdatedBy(CurrentUser.email());
        return view(packages.save(p), Audience.RIDER_BOARD);
    }

    @Transactional
    public PackageView pickUp(Long packageId) {
        OrderPackage p = packages.findByIdForUpdate(packageId).orElseThrow(() -> new IllegalStateException("Package not found."));
        boolean staff = CurrentUser.has("orders.fulfil");
        if (staff && p.getRiderId() == null) {
            // our own delivery: straight from "ready" to "on the way", no rider pay
            requireStatus(p, OrderPackage.READY_FOR_PICKUP, "Pack it first.");
            p.setRiderPay(BigDecimal.ZERO);
        } else {
            requireOwnJob(p, staff);
            requireStatus(p, OrderPackage.ASSIGNED, "Accept the job first.");
        }
        p.setStatus(OrderPackage.PICKED_UP);
        p.setPickedUpAt(Instant.now());
        p.setUpdatedBy(CurrentUser.email());
        packages.save(p);

        orders.findById(p.getOrderId()).ifPresent(order -> {
            if ("CONFIRMED".equals(order.getOrderStatus())) {
                order.setOrderStatus("SHIPPED");
                order.setUpdatedBy(CurrentUser.email());
                orders.save(order);
            }
            notify.customer(order, new NotificationService.Note("ON_THE_WAY", "Your order #" + order.getOrderId() + " is on the way",
                    "Give the rider your delivery code " + p.getDeliveryCode() + " when you receive it. Do not share it before.",
                    "/orders/" + order.getOrderId()), true,
                    "DK/Phar: order #" + order.getOrderId() + " is on the way. Give the rider code " + p.getDeliveryCode() + " at the door.");
        });
        if (p.getSellerId() != null) {
            notify.seller(p.getSellerId(), new NotificationService.Note("PICKED_UP", "Package for order #" + p.getOrderId() + " collected",
                    "The rider has it. Your money is booked when it is delivered.", "/seller"), false);
        }
        return view(p, staff ? Audience.STAFF : Audience.RIDER);
    }

    /** Handed over. The rider must enter the customer's code; staff may confirm without it. Books the earnings. */
    @Transactional
    public PackageView deliver(Long packageId, String code) {
        OrderPackage p = packages.findByIdForUpdate(packageId).orElseThrow(() -> new IllegalStateException("Package not found."));
        boolean staff = CurrentUser.has("orders.fulfil");
        requireOwnJob(p, staff);
        requireStatus(p, OrderPackage.PICKED_UP, "Pick the package up first.");

        String given = code == null ? "" : code.trim();
        if (!staff && !given.equals(p.getDeliveryCode())) {
            throw new IllegalStateException("That code is not right. Ask the customer for the 4-digit code on their order page.");
        }

        p.setStatus(OrderPackage.DELIVERED);
        p.setDeliveredAt(Instant.now());
        p.setUpdatedBy(CurrentUser.email());
        packages.save(p);

        if (p.getSellerId() != null && p.getSellerEarning().signum() > 0
                && !ledger.existsByPackageIdAndPartyTypeAndEntryType(p.getId(), LedgerEntry.SELLER, LedgerEntry.SALE)) {
            book(LedgerEntry.SELLER, p.getSellerId(), LedgerEntry.SALE, p.getSellerEarning(), p,
                    "Sale Nu. " + p.getItemsSubtotal() + " minus " + p.getCommissionPercent().stripTrailingZeros().toPlainString() + "% commission");
        }
        if (p.getRiderId() != null && p.getRiderPay().signum() > 0
                && !ledger.existsByPackageIdAndPartyTypeAndEntryType(p.getId(), LedgerEntry.RIDER, LedgerEntry.DELIVERY)) {
            book(LedgerEntry.RIDER, p.getRiderId(), LedgerEntry.DELIVERY, p.getRiderPay(), p, "Delivery of package #" + p.getId());
        }

        // every package delivered = the order is complete
        List<OrderPackage> all = packages.findByOrderIdOrderByIdAsc(p.getOrderId());
        boolean done = all.stream().allMatch(x -> OrderPackage.DELIVERED.equals(x.getStatus()) || OrderPackage.CANCELLED.equals(x.getStatus()));
        if (done) {
            orders.findById(p.getOrderId()).ifPresent(order -> {
                order.setOrderStatus("COMPLETED");
                order.setUpdatedBy(CurrentUser.email());
                orders.save(order);
            });
        }
        orders.findById(p.getOrderId()).ifPresent(order -> notify.customer(order, new NotificationService.Note("DELIVERED",
                done ? "Order #" + order.getOrderId() + " delivered" : "Part of order #" + order.getOrderId() + " delivered",
                "Thank you for shopping with DK/Phar." + (done ? "" : " The rest comes in a separate package."),
                "/orders/" + order.getOrderId()), true, null));
        if (p.getSellerId() != null && p.getSellerEarning().signum() > 0) {
            notify.seller(p.getSellerId(), new NotificationService.Note("EARNED", "Delivered: Nu. " + p.getSellerEarning() + " earned",
                    "Order #" + p.getOrderId() + " reached the customer. It is added to what we owe you.", "/seller"), true);
        }
        if (p.getRiderId() != null && p.getRiderPay().signum() > 0) {
            notify.rider(p.getRiderId(), new NotificationService.Note("EARNED", "Nu. " + p.getRiderPay() + " added to your earnings",
                    "Delivery of order #" + p.getOrderId() + " done.", "/rider"), false);
        }
        return view(p, staff ? Audience.STAFF : Audience.RIDER);
    }

    // ================= Customer and staff =================

    /** The packages of one order. The customer sees the delivery code; staff see everything except the code. */
    public List<PackageView> forOrder(Long orderId, boolean asCustomer) {
        Audience audience = asCustomer ? Audience.CUSTOMER : Audience.STAFF;
        return packages.findByOrderIdOrderByIdAsc(orderId).stream().map(p -> view(p, audience)).toList();
    }

    public List<PackageView> allForStaff(String status) {
        List<OrderPackage> list = status == null || status.isBlank()
                ? packages.findAllByOrderByIdDesc()
                : packages.findByStatusInOrderByIdDesc(List.of(status.trim().toUpperCase()));
        return list.stream().map(p -> view(p, Audience.STAFF)).toList();
    }

    public List<OrderPackage> rawForSeller(Long sellerId) {
        return packages.findBySellerIdOrderByIdDesc(sellerId);
    }

    public List<OrderPackage> rawForRider(Long riderId) {
        return packages.findByRiderIdOrderByIdDesc(riderId);
    }

    // ================= Helpers =================

    enum Audience { CUSTOMER, SELLER, RIDER_BOARD, RIDER, STAFF }

    private void requireOwnJob(OrderPackage p, boolean staff) {
        if (staff) {
            return;
        }
        RiderProfile rider = requireApprovedRider();
        if (!Objects.equals(rider.getId(), p.getRiderId())) {
            throw new AccessDeniedException("This is not your job.");
        }
    }

    private static void requireStatus(OrderPackage p, String expected, String message) {
        if (!expected.equals(p.getStatus())) {
            throw new IllegalStateException(message);
        }
    }

    private void book(String partyType, Long partyId, String type, BigDecimal amount, OrderPackage p, String note) {
        LedgerEntry e = new LedgerEntry();
        e.setPartyType(partyType);
        e.setPartyId(partyId);
        e.setEntryType(type);
        e.setAmount(amount);
        e.setOrderId(p.getOrderId());
        e.setPackageId(p.getId());
        e.setNote(note);
        e.setCreatedBy(CurrentUser.email());
        ledger.save(e);
    }

    private PackageView view(OrderPackage p, Audience who) {
        Order order = orders.findById(p.getOrderId()).orElse(null);
        SellerProfile seller = p.getSellerId() == null ? null : sellers.findById(p.getSellerId()).orElse(null);
        RiderProfile rider = p.getRiderId() == null ? null : riders.findById(p.getRiderId()).orElse(null);

        List<PackageLine> lines = new ArrayList<>();
        int count = 0;
        for (OrderItem line : orderItems.findByOrderId(p.getOrderId())) {
            if (!Objects.equals(line.getPackageId(), p.getId())) {
                continue;
            }
            ItemMaster item = itemsRepo.findById(line.getItemId()).orElse(null);
            lines.add(new PackageLine(line.getItemId(), item == null ? "Item #" + line.getItemId() : item.getItemName(),
                    item == null ? null : item.getImagePath(), line.getQuantity(), line.getUnitPrice()));
            count += line.getQuantity();
        }

        boolean riderOnJob = who == Audience.RIDER && RIDER_ACTIVE.contains(p.getStatus());
        boolean showCustomerContact = who == Audience.STAFF || riderOnJob;
        boolean showMoney = who == Audience.STAFF || who == Audience.SELLER;
        boolean showRiderContact = who != Audience.RIDER_BOARD && who != Audience.SELLER;

        return new PackageView(
                p.getId(), p.getOrderId(), p.getStatus(),
                p.getSellerId(), seller == null ? "DK/Phar" : seller.getShopName(),
                who == Audience.SELLER || who == Audience.CUSTOMER ? null : (seller == null ? null : seller.getPhone()),
                who == Audience.CUSTOMER ? null : p.getPickupAddress(),
                seller == null ? null : seller.getTown(),
                who == Audience.RIDER_BOARD || order == null ? null : order.getCustomerName(),
                showCustomerContact && order != null ? order.getCustomerPhone() : null,
                who == Audience.SELLER ? null : p.getDropAddress(),
                p.getRiderId(),
                rider == null ? null : rider.getUser().getName(),
                showRiderContact && rider != null ? rider.getPhone() : null,
                rider == null ? null : rider.getVehicleType() + (rider.getVehicleNumber() == null ? "" : " " + rider.getVehicleNumber()),
                showMoney ? p.getItemsSubtotal() : (who == Audience.CUSTOMER ? p.getItemsSubtotal() : null),
                showMoney ? p.getCommissionPercent() : null,
                showMoney ? p.getCommissionAmount() : null,
                showMoney ? p.getSellerEarning() : null,
                who == Audience.CUSTOMER || who == Audience.STAFF ? p.getDeliveryFee() : null,
                who == Audience.RIDER || who == Audience.RIDER_BOARD || who == Audience.STAFF ? p.getRiderPay() : null,
                p.getDeliverySize() == null ? DeliverySize.SMALL.name() : p.getDeliverySize(),
                p.getDistanceKm(), Boolean.TRUE.equals(p.getDistanceEstimated()),
                who == Audience.CUSTOMER ? null : p.getPickupLatitude(),
                who == Audience.CUSTOMER ? null : p.getPickupLongitude(),
                showCustomerContact ? p.getDropLatitude() : null,
                showCustomerContact ? p.getDropLongitude() : null,
                who == Audience.CUSTOMER && !OrderPackage.CANCELLED.equals(p.getStatus()) ? p.getDeliveryCode() : null,
                count,
                who == Audience.RIDER_BOARD ? List.of() : lines,
                p.getCreatedAt(), p.getPackedAt(), p.getAssignedAt(), p.getPickedUpAt(), p.getDeliveredAt());
    }
}
