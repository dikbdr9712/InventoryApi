package com.api.inventory.service;

import com.api.inventory.entity.*;
import com.api.inventory.repository.*;
import com.api.inventory.security.AccessControlService;
import com.api.inventory.security.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The order board: every online order, where it is, who has it, and what is late.
 *
 *   1 PAYMENT_DUE  placed, the customer has not paid yet
 *   2 VERIFY       a payment waits for staff (a transfer to check, a bank payment without an answer, a problem)
 *   3 PACK         paid: to pack (a staff member for our own shop, or the seller)
 *   4 READY        packed, waiting for a driver (or for us to deliver it)
 *     COLLECT      packed, waiting for the customer to collect it ("Pick up myself")
 *   5 ASSIGNED     a driver is on the way to collect it
 *   6 ON_THE_WAY   picked up, going to the customer
 *   7 DELIVERED    delivered (the last 2 days are shown)
 *   - CANCELLED    cancelled (the last 2 days are shown)
 *
 * Payment steps are per order; from packing on, per package (an order with two sellers has two packages that move
 * separately). Each item says how long it has been in its step and whether that is longer than the shop's target.
 *
 * The board shows the orders PLACED in a period (today, this week, ... or any range; this month when none is given).
 * Orders placed earlier that are still not delivered are never lost: they are counted (olderOpen) and added, marked
 * "older", when asked for (includeOlder).
 */
@Service
public class OrderBoardService {

    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Thimphu");

    public record Targets(int verifyMinutes, int packMinutes, int pickupMinutes, int deliverMinutes, int unpaidMinutes, int collectMinutes) {
    }

    /** placed: orders placed in the period. olderOpen: orders placed before it, still not delivered (shown when asked). */
    public record Counts(int awaitingPayment, int verify, int toPack, int toPackNotStarted, int ready, int riderComing,
                         int onTheWay, int delivered, int cancelled, int late, int needsAction, int placed, int olderOpen, int toCollect) {
    }

    public record Line(String name, int quantity, BigDecimal unitPrice) {
    }

    public record Item(String key, String stage, Long orderId, Long packageId, int packageNo, int packageCount, boolean legacy,
                       String customerName, String customerPhone, String customerEmail, String dropAddress,
                       Long sellerId, String sellerName, String sellerPhone, String pickupAddress,
                       List<Line> lines, int itemCount, BigDecimal amount, BigDecimal orderTotal,
                       String paymentStatus, String paymentMethod, String journal, String paymentNote,
                       String verifiedBy, Instant verifiedAt,
                       String packerEmail, String packerName, Instant packingStartedAt,
                       Long riderId, String riderName, String riderPhone, String riderVehicle, String riderAssignedByName,
                       String courierName, String deliverySize, BigDecimal distanceKm,
                       Instant placedAt, Instant stageSince, long minutesInStage, Integer targetMinutes, boolean late,
                       Instant packedAt, Instant assignedAt, Instant pickedUpAt, Instant deliveredAt, Instant cancelledAt,
                       BigDecimal deliveryFee, BigDecimal riderPay, String pickupTown, boolean older,
                       boolean selfPickup, String handedOverByName) {
    }

    /** packing: packages they are packing now; packed: packed in the period. */
    public record Packer(String email, String name, String role, int packing, int packed) {
    }

    /** carries: the biggest delivery size the driver's vehicle takes (SMALL, MEDIUM, LARGE, BULKY). */
    public record Rider(Long id, String name, String phone, String vehicleType, String vehicle, String carries, int active, int max,
                        int delivered, boolean available) {
    }

    /** from / to: the period (days, both included, shop time). includeOlder: older open orders are in the list. */
    public record Board(Instant generatedAt, LocalDate from, LocalDate to, boolean includeOlder, Targets targets, Counts counts,
                        List<Item> items, List<Packer> packers, List<Rider> riders, String me, boolean canAssign) {
    }

    private final OrderRepository orders;
    private final OrderPackageRepository packages;
    private final OrderItemRepository orderItems;
    private final ItemMasterRepository itemsRepo;
    private final PaymentRepository payments;
    private final BankPaymentRepository bankPayments;
    private final SellerProfileRepository sellers;
    private final RiderProfileRepository riders;
    private final UserRepository users;
    private final AccessControlService access;
    private final Targets targets;

    public OrderBoardService(OrderRepository orders, OrderPackageRepository packages, OrderItemRepository orderItems,
                             ItemMasterRepository itemsRepo, PaymentRepository payments, BankPaymentRepository bankPayments,
                             SellerProfileRepository sellers, RiderProfileRepository riders, UserRepository users,
                             AccessControlService access,
                             @Value("${app.orders.target.verify-minutes:120}") int verifyMinutes,
                             @Value("${app.orders.target.pack-minutes:240}") int packMinutes,
                             @Value("${app.orders.target.pickup-minutes:120}") int pickupMinutes,
                             @Value("${app.orders.target.deliver-minutes:180}") int deliverMinutes,
                             @Value("${app.orders.target.unpaid-minutes:1440}") int unpaidMinutes,
                             @Value("${app.orders.target.collect-minutes:4320}") int collectMinutes) {
        this.orders = orders;
        this.packages = packages;
        this.orderItems = orderItems;
        this.itemsRepo = itemsRepo;
        this.payments = payments;
        this.bankPayments = bankPayments;
        this.sellers = sellers;
        this.riders = riders;
        this.users = users;
        this.access = access;
        this.targets = new Targets(verifyMinutes, packMinutes, pickupMinutes, deliverMinutes, unpaidMinutes, collectMinutes);
    }

    @Transactional(readOnly = true)
    public Board board(LocalDate from, LocalDate to, boolean includeOlder) {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(SHOP_ZONE);
        LocalDate first = from != null ? from : today.withDayOfMonth(1);
        LocalDate last = to != null ? to : today;
        if (last.isBefore(first)) {
            LocalDate swap = first;
            first = last;
            last = swap;
        }
        Instant periodStart = first.atStartOfDay(SHOP_ZONE).toInstant();
        Instant periodEnd = last.plusDays(1).atStartOfDay(SHOP_ZONE).toInstant();
        LocalDateTime start = LocalDateTime.ofInstant(periodStart, ZoneId.systemDefault()); // orders keep server-local time
        LocalDateTime end = LocalDateTime.ofInstant(periodEnd, ZoneId.systemDefault());

        List<Order> list = new ArrayList<>(orders.findOnlinePlacedBetween(start, end));
        int placedInPeriod = list.size();
        List<Order> olderOpen = orders.findOnlineOpenPlacedBefore(start);
        Set<Long> olderIds = new HashSet<>();
        if (includeOlder) {
            olderOpen.forEach(o -> olderIds.add(o.getOrderId()));
            list.addAll(olderOpen);
        }
        List<Long> ids = list.stream().map(Order::getOrderId).toList();
        Map<Long, List<OrderPackage>> packagesByOrder = ids.isEmpty() ? Map.of()
                : packages.findByOrderIdIn(ids).stream().sorted(Comparator.comparing(OrderPackage::getId))
                .collect(Collectors.groupingBy(OrderPackage::getOrderId));
        Map<Long, List<OrderItem>> linesByOrder = ids.isEmpty() ? Map.of()
                : orderItems.findByOrderIdIn(ids).stream().collect(Collectors.groupingBy(OrderItem::getOrderId));
        Map<Long, Payment> paymentByOrder = ids.isEmpty() ? Map.of()
                : payments.findByOrderIdIn(ids).stream().collect(Collectors.toMap(Payment::getOrderId, Function.identity(), (a, b) -> b));
        Set<Long> bankToCheck = bankPayments.findByStatusOrderByCreatedAtAsc(BankPayment.CHECK_BANK).stream()
                .map(BankPayment::getOrderId).collect(Collectors.toSet());
        Map<Long, String> itemNames = new HashMap<>();
        Set<Long> itemIds = linesByOrder.values().stream().flatMap(List::stream).map(OrderItem::getItemId).collect(Collectors.toSet());
        itemsRepo.findAllById(itemIds).forEach(i -> itemNames.put(i.getItemId(), i.getItemName()));
        Map<Long, SellerProfile> sellerById = new HashMap<>();
        sellers.findAll().forEach(s -> sellerById.put(s.getId(), s));
        Map<Long, RiderProfile> riderById = new HashMap<>();
        List<RiderProfile> allRiders = riders.findAllByOrderByCreatedAtDesc();
        allRiders.forEach(r -> riderById.put(r.getId(), r));
        Map<String, User> userByEmail = new HashMap<>();
        users.findAll().forEach(u -> {
            if (u.getEmail() != null) {
                userByEmail.put(u.getEmail().toLowerCase(Locale.ROOT), u);
            }
        });
        Function<String, String> nameOf = email -> {
            if (email == null) {
                return null;
            }
            User u = userByEmail.get(email.toLowerCase(Locale.ROOT));
            return u == null ? email : u.getName();
        };

        List<Item> items = new ArrayList<>();
        for (Order o : list) {
            List<OrderPackage> pkgs = packagesByOrder.getOrDefault(o.getOrderId(), List.of());
            List<OrderItem> lines = linesByOrder.getOrDefault(o.getOrderId(), List.of());
            Payment payment = paymentByOrder.get(o.getOrderId());
            String status = up(o.getOrderStatus());
            String pay = up(o.getPaymentStatus());
            Instant placed = instant(o.getCreatedAt());
            OrderView ov = new OrderView(o, payment, lines, itemNames, nameOf, olderIds.contains(o.getOrderId()));

            if ("CANCELLED".equals(status)) {
                Instant at = instant(o.getUpdatedAt());
                items.add(ov.item("CANCELLED", null, 0, pkgs.size(), at == null ? placed : at, now, null, at));
                continue;
            }

            // ---- payment steps: per order ----
            boolean submitted = payment != null && "pending".equalsIgnoreCase(payment.getStatus());
            boolean confirmed = Set.of("CONFIRMED", "SHIPPED", "COMPLETED").contains(status);
            if (!confirmed) {
                if (bankToCheck.contains(o.getOrderId())) {
                    items.add(ov.withNote("The bank did not answer when asked to take the money: ask the bank, then settle it in Verify payments.")
                            .item("VERIFY", null, 0, pkgs.size(), placed, now, targets.verifyMinutes(), null));
                } else if (submitted || "PENDING_INFO".equals(pay) || "PAID".equals(pay) || "PARTIALLY_PAID".equals(pay)) {
                    String note = "PENDING_INFO".equals(pay) ? "Waiting for the customer to send more information."
                            : "PAID".equals(pay) ? "Paid, but the order was not confirmed (" + o.getOrderStatus() + "): check the stock."
                            : "Check this transfer in the bank account.";
                    Instant since0 = payment != null && payment.getPaymentDate() != null ? instant(payment.getPaymentDate()) : placed;
                    items.add(ov.withNote(note).item("VERIFY", null, 0, pkgs.size(), since0, now,
                            "PENDING_INFO".equals(pay) ? null : targets.verifyMinutes(), null));
                } else {
                    String note = "REJECTED".equals(pay) || "FAILED".equals(pay) ? "The payment was refused: waiting for the customer to pay again." : null;
                    items.add(ov.withNote(note).item("PAYMENT_DUE", null, 0, pkgs.size(), placed, now, targets.unpaidMinutes(), null));
                }
                continue;
            }

            Instant paidAt = o.getPaymentVerifiedAt() != null ? o.getPaymentVerifiedAt() : placed;

            // ---- orders from before packages existed: one step for the whole order ----
            if (pkgs.isEmpty()) {
                switch (status) {
                    case "CONFIRMED" -> items.add(ov.legacy().item("PACK", null, 0, 0, paidAt, now, targets.packMinutes(), null));
                    case "SHIPPED" -> items.add(ov.legacy().item("ON_THE_WAY", null, 0, 0,
                            o.getUpdatedAt() == null ? paidAt : instant(o.getUpdatedAt()), now, targets.deliverMinutes(), null));
                    default -> items.add(ov.legacy().item("DELIVERED", null, 0, 0,
                            o.getUpdatedAt() == null ? paidAt : instant(o.getUpdatedAt()), now, null, null));
                }
                continue;
            }

            // ---- packing and delivery: per package ----
            int n = 0;
            for (OrderPackage p : pkgs) {
                n++;
                String stage;
                Instant stageSince;
                Integer target;
                switch (p.getStatus()) {
                    case OrderPackage.TO_PACK -> { stage = "PACK"; stageSince = paidAt; target = targets.packMinutes(); }
                    case OrderPackage.READY_FOR_PICKUP -> {
                        stageSince = nz(p.getPackedAt(), paidAt);
                        if (p.isSelfPickup()) { stage = "COLLECT"; target = targets.collectMinutes(); }
                        else { stage = "READY"; target = targets.pickupMinutes(); }
                    }
                    case OrderPackage.ASSIGNED -> { stage = "ASSIGNED"; stageSince = nz(p.getPackedAt(), paidAt); target = targets.pickupMinutes(); }
                    case OrderPackage.PICKED_UP -> { stage = "ON_THE_WAY"; stageSince = nz(p.getPickedUpAt(), paidAt); target = targets.deliverMinutes(); }
                    case OrderPackage.DELIVERED -> { stage = "DELIVERED"; stageSince = nz(p.getDeliveredAt(), paidAt); target = null; }
                    case OrderPackage.CANCELLED -> { stage = "CANCELLED"; stageSince = nz(p.getCancelledAt(), paidAt); target = null; }
                    default -> { // PENDING_PAYMENT on a confirmed order: should not happen, staff look at it
                        items.add(ov.withNote("Paid, but this package is still waiting for payment: confirm the payment again.")
                                .item("VERIFY", p, n, pkgs.size(), paidAt, now, targets.verifyMinutes(), null));
                        continue;
                    }
                }
                SellerProfile seller = p.getSellerId() == null ? null : sellerById.get(p.getSellerId());
                RiderProfile rider = p.getRiderId() == null ? null : riderById.get(p.getRiderId());
                items.add(ov.forPackage(p, seller, rider).item(stage, p, n, pkgs.size(), stageSince, now, target, null));
            }
        }

        // the oldest (most urgent) first inside each step
        items.sort(Comparator.comparing((Item i) -> !i.late()).thenComparing(Item::stageSince, Comparator.nullsLast(Comparator.naturalOrder())));

        int awaiting = 0, verify = 0, toPack = 0, notStarted = 0, ready = 0, collect = 0, coming = 0, onWay = 0, delivered = 0, cancelled = 0, late = 0;
        for (Item i : items) {
            switch (i.stage()) {
                case "PAYMENT_DUE" -> awaiting++;
                case "VERIFY" -> verify++;
                case "PACK" -> {
                    toPack++;
                    if (i.packerEmail() == null && i.sellerId() == null) {
                        notStarted++;
                    }
                }
                case "READY" -> ready++;
                case "COLLECT" -> collect++;
                case "ASSIGNED" -> coming++;
                case "ON_THE_WAY" -> onWay++;
                case "DELIVERED" -> delivered++;
                case "CANCELLED" -> cancelled++;
                default -> {
                }
            }
            if (i.late() && !"PAYMENT_DUE".equals(i.stage())) {
                late++;
            }
        }
        Counts counts = new Counts(awaiting, verify, toPack, notStarted, ready, coming, onWay, delivered, cancelled, late,
                verify + toPack + ready, placedInPeriod, olderOpen.size(), collect);

        // ---- the team ----
        List<Packer> packers = new ArrayList<>();
        for (User u : userByEmail.values()) {
            if (!u.isActive() || u.getRole() == null || !access.permissionsOf(u.getRole().getName()).contains("orders.fulfil")) {
                continue;
            }
            int packing = 0, packed = 0;
            for (Item i : items) {
                if (u.getEmail().equalsIgnoreCase(i.packerEmail())) {
                    if ("PACK".equals(i.stage())) {
                        packing++;
                    } else if (within(i.packedAt(), periodStart, periodEnd)) {
                        packed++;
                    }
                }
            }
            packers.add(new Packer(u.getEmail(), u.getName(), u.getRole().getName(), packing, packed));
        }
        packers.sort(Comparator.comparing(Packer::packing).reversed().thenComparing(Packer::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        List<Rider> riderList = new ArrayList<>();
        for (RiderProfile r : allRiders) {
            if (!r.isApproved()) {
                continue;
            }
            int active = 0, deliveredInPeriod = 0;
            for (OrderPackage p : packages.findByRiderIdOrderByIdDesc(r.getId())) {
                if (OrderPackage.ASSIGNED.equals(p.getStatus()) || OrderPackage.PICKED_UP.equals(p.getStatus())) {
                    active++;
                } else if (OrderPackage.DELIVERED.equals(p.getStatus()) && within(p.getDeliveredAt(), periodStart, periodEnd)) {
                    deliveredInPeriod++;
                }
            }
            boolean available = !r.licenceExpired() && active < PackageService.MAX_ACTIVE_JOBS_PER_RIDER;
            riderList.add(new Rider(r.getId(), r.getUser().getName(), r.getPhone(), r.getVehicleType(),
                    r.getVehicleType() + (r.getVehicleNumber() == null ? "" : " " + r.getVehicleNumber()),
                    DeliverySize.carriedBy(r.getVehicleType()).name(), active, PackageService.MAX_ACTIVE_JOBS_PER_RIDER, deliveredInPeriod, available));
        }
        riderList.sort(Comparator.comparing(Rider::available).reversed().thenComparing(Rider::active));

        return new Board(now, first, last, includeOlder, targets, counts, items, packers, riderList, CurrentUser.email(),
                CurrentUser.has("orders.assign"));
    }

    // ================= Building one item =================

    /** What is the same for every item of one order; forPackage / withNote / legacy add the rest. */
    private final class OrderView {
        final Order o;
        final Payment payment;
        final List<OrderItem> lines;
        final Map<Long, String> itemNames;
        final Function<String, String> nameOf;
        final boolean older;
        OrderPackage pkg;
        SellerProfile seller;
        RiderProfile rider;
        String note;
        boolean legacy;

        OrderView(Order o, Payment payment, List<OrderItem> lines, Map<Long, String> itemNames, Function<String, String> nameOf,
                  boolean older) {
            this.o = o;
            this.payment = payment;
            this.lines = lines;
            this.itemNames = itemNames;
            this.nameOf = nameOf;
            this.older = older;
        }

        OrderView copy() {
            OrderView c = new OrderView(o, payment, lines, itemNames, nameOf, older);
            c.pkg = pkg;
            c.seller = seller;
            c.rider = rider;
            c.note = note;
            c.legacy = legacy;
            return c;
        }

        OrderView withNote(String text) {
            OrderView c = copy();
            c.note = text;
            return c;
        }

        OrderView legacy() {
            OrderView c = copy();
            c.legacy = true;
            return c;
        }

        OrderView forPackage(OrderPackage p, SellerProfile s, RiderProfile r) {
            OrderView c = copy();
            c.pkg = p;
            c.seller = s;
            c.rider = r;
            return c;
        }

        Item item(String stage, OrderPackage p, int no, int count, Instant stageSince, Instant now, Integer target, Instant cancelledAt) {
            List<Line> shown = new ArrayList<>();
            int itemCount = 0;
            BigDecimal itemsTotal = BigDecimal.ZERO;
            for (OrderItem l : lines) {
                if (pkg != null && !Objects.equals(l.getPackageId(), pkg.getId())) {
                    continue;
                }
                int q = l.getQuantity() == null ? 0 : l.getQuantity();
                BigDecimal unit = l.getUnitPrice() == null ? BigDecimal.ZERO : l.getUnitPrice();
                shown.add(new Line(itemNames.getOrDefault(l.getItemId(), "Item #" + l.getItemId()), q, unit));
                itemCount += q;
                itemsTotal = itemsTotal.add(unit.multiply(BigDecimal.valueOf(q)));
            }
            long minutes = stageSince == null ? 0 : Math.max(0, Duration.between(stageSince, now).toMinutes());
            boolean late = target != null && minutes > target;
            String journal = payment == null ? null : payment.getJournalNumber();
            String verifiedBy = o.getPaymentVerifiedBy();
            String verifiedByName = verifiedBy == null ? null
                    : verifiedBy.startsWith("online:") ? ("online:BANK".equals(verifiedBy) ? "The bank (RMA Payment Gateway)" : "Online payment")
                    : nameOf.apply(verifiedBy);
            BigDecimal amount = pkg == null ? o.getTotalAmount() : itemsTotal.add(pkg.getDeliveryFee() == null ? BigDecimal.ZERO : pkg.getDeliveryFee());
            return new Item(
                    pkg == null ? "o" + o.getOrderId() : "p" + pkg.getId(), stage, o.getOrderId(), pkg == null ? null : pkg.getId(), no, count, legacy,
                    o.getCustomerName(), o.getCustomerPhone(), o.getCustomerEmail(), pkg != null && pkg.getDropAddress() != null ? pkg.getDropAddress() : o.getAddress(),
                    pkg == null ? null : pkg.getSellerId(), pkg == null ? null : seller == null ? "DP DrukBazaars" : seller.getShopName(),
                    seller == null ? null : seller.getPhone(), pkg == null ? null : pkg.getPickupAddress(),
                    shown, itemCount, amount, o.getTotalAmount(),
                    o.getPaymentStatus(), payment == null ? null : payment.getPaymentMethod(), journal, note,
                    verifiedByName, o.getPaymentVerifiedAt(),
                    pkg == null ? null : pkg.getPackerEmail(), pkg == null ? null : nameOf.apply(pkg.getPackerEmail()),
                    pkg == null ? null : pkg.getPackingStartedAt(),
                    rider == null ? null : rider.getId(), rider == null ? null : rider.getUser().getName(), rider == null ? null : rider.getPhone(),
                    rider == null ? null : rider.getVehicleType() + (rider.getVehicleNumber() == null ? "" : " " + rider.getVehicleNumber()),
                    pkg == null ? null : nameOf.apply(pkg.getRiderAssignedBy()),
                    pkg == null ? null : nameOf.apply(pkg.getCourierEmail()),
                    pkg == null ? null : pkg.getDeliverySize(), pkg == null ? null : pkg.getDistanceKm(),
                    instant(o.getCreatedAt()), stageSince, minutes, target, late,
                    pkg == null ? null : pkg.getPackedAt(), pkg == null ? null : pkg.getAssignedAt(),
                    pkg == null ? null : pkg.getPickedUpAt(), pkg == null ? null : pkg.getDeliveredAt(),
                    pkg == null ? cancelledAt : pkg.getCancelledAt(),
                    pkg == null ? null : pkg.getDeliveryFee(), pkg == null ? null : pkg.getRiderPay(),
                    seller == null ? null : seller.getTown(), older,
                    pkg != null && pkg.isSelfPickup(), pkg == null || pkg.getHandedOverBy() == null ? null : nameOf.apply(pkg.getHandedOverBy()));
        }
    }

    private static boolean within(Instant t, Instant from, Instant to) {
        return t != null && !t.isBefore(from) && t.isBefore(to);
    }

    private static Instant instant(LocalDateTime t) {
        return t == null ? null : t.atZone(ZoneId.systemDefault()).toInstant();
    }

    private static Instant nz(Instant a, Instant b) {
        return a != null ? a : b;
    }

    private static String up(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }
}
