package com.api.inventory.service;

import com.api.inventory.dto.ReturnDTO.ReturnLineRequest;
import com.api.inventory.dto.ReturnDTO.ReturnLineView;
import com.api.inventory.dto.ReturnDTO.ReturnRequest;
import com.api.inventory.dto.ReturnDTO.ReturnView;
import com.api.inventory.dto.ReturnDTO.ReturnableLine;
import com.api.inventory.dto.ReturnDTO.ReturnableOrder;
import com.api.inventory.entity.LedgerEntry;
import com.api.inventory.entity.OrderPackage;
import com.api.inventory.entity.SalesReturn;
import com.api.inventory.entity.SalesReturnItem;
import com.api.inventory.repository.LedgerEntryRepository;
import com.api.inventory.repository.OrderPackageRepository;
import com.api.inventory.repository.SellerProfileRepository;
import com.api.inventory.repository.SalesReturnItemRepository;
import com.api.inventory.repository.SalesReturnRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Takes items back from an earlier sale.
 *
 * It reads the existing orders, order_items, inventory_stock and transactions tables with plain SQL,
 * so it does not depend on your other entity classes.
 *
 * What a customer gets back for one item = the price they really paid (order_items.unit_price already has
 * the discount in it) plus that sale's tax rate. The tax rate is worked out from the sale itself.
 */
@Service
public class SalesReturnService {

    /** A problem the staff member can understand and fix, with the HTTP status that fits it. */
    public static class ReturnException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final HttpStatus status;

        public ReturnException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatus getStatus() {
            return status;
        }
    }

    private static final Set<String> REASONS = Set.of("DAMAGED", "WRONG_ITEM", "CHANGED_MIND", "OTHER");
    private static final Set<String> METHODS = Set.of("CASH", "ORIGINAL");
    private static final BigDecimal TOLERANCE = new BigDecimal("0.05"); // rounding differences of a few cents

    /** deliveryFee: online orders only (never refunded: the delivery took place). */
    private record OrderRow(Long orderId, String status, BigDecimal total, BigDecimal tax, BigDecimal deliveryFee,
                            LocalDateTime createdAt, String customerName) {
        /** What the customer paid for the items themselves (with tax), the most that can ever be refunded. */
        BigDecimal itemsPaid() {
            return total.subtract(deliveryFee);
        }
    }

    /** packageId: the marketplace package the line belongs to (online orders), or null. */
    private record LineRow(Long orderItemId, Long itemId, int quantity, BigDecimal unitPrice, Long packageId) {
    }

    @PersistenceContext
    private EntityManager em;

    private final SalesReturnRepository returns;
    private final SalesReturnItemRepository returnItems;

    /** How many days after the sale a return is still accepted. Change with app.returns.window-days. */
    @Value("${app.returns.window-days:7}")
    private int windowDays;

    private StockService stockService;
    private OrderPackageRepository packages;
    private LedgerEntryRepository ledger;
    private SellerProfileRepository sellers;
    private NotificationService notify;

    @org.springframework.beans.factory.annotation.Autowired
    void setStockService(StockService stockService) {
        this.stockService = stockService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setMarketplace(OrderPackageRepository packages, LedgerEntryRepository ledger, SellerProfileRepository sellers,
                        NotificationService notify) {
        this.packages = packages;
        this.ledger = ledger;
        this.sellers = sellers;
        this.notify = notify;
    }

    public SalesReturnService(SalesReturnRepository returns, SalesReturnItemRepository returnItems) {
        this.returns = returns;
        this.returnItems = returnItems;
    }

    // ------------------------------------------------------------------ what can be returned

    @Transactional(readOnly = true)
    public ReturnableOrder returnable(Long orderId) {
        OrderRow order = loadOrder(orderId);
        List<LineRow> lines = loadLines(orderId, false);

        String problem = blocker(order);
        BigDecimal rate = BigDecimal.ZERO;
        if (problem == null) {
            try {
                rate = taxRate(order, lines);
            } catch (ReturnException e) {
                problem = e.getMessage();
            }
        }

        List<ReturnableLine> view = new ArrayList<>();
        for (LineRow line : lines) {
            long returned = returnItems.totalReturnedFor(line.orderItemId());
            BigDecimal unitRefund = problem == null ? refundPerUnit(line, rate) : BigDecimal.ZERO;
            view.add(new ReturnableLine(line.orderItemId(), line.itemId(), line.quantity(), returned,
                    Math.max(0, line.quantity() - returned), unitRefund));
        }
        return new ReturnableOrder(orderId, order.status(), problem == null, problem, daysLeft(order),
                order.total(), refundedSoFar(orderId), view);
    }

    @Transactional(readOnly = true)
    public List<ReturnView> list(Long orderId) {
        List<ReturnView> result = new ArrayList<>();
        for (SalesReturn r : returns.findByOrderIdOrderByCreatedAtDesc(orderId)) {
            result.add(toView(r));
        }
        return result;
    }

    // ------------------------------------------------------------------ making a return

    /** Everything below happens together: if anything fails, nothing is saved and no stock moves. */
    @Transactional
    public ReturnView createReturn(Long orderId, ReturnRequest request, String staffEmail) {
        // 1. what was asked
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new ReturnException(HttpStatus.BAD_REQUEST, "Choose at least one item to return.");
        }
        String reason = clean(request.reason()).toUpperCase();
        if (!REASONS.contains(reason)) {
            throw new ReturnException(HttpStatus.BAD_REQUEST, "Choose a reason for the return.");
        }
        String method = clean(request.refundMethod()).toUpperCase();
        if (!METHODS.contains(method)) {
            throw new ReturnException(HttpStatus.BAD_REQUEST, "Choose how the money is given back (cash or original payment method).");
        }
        String note = clean(request.note());
        if (note.length() > 255) {
            throw new ReturnException(HttpStatus.BAD_REQUEST, "The note is too long (255 characters at most).");
        }

        // 2. the sale: lock its lines so two returns at the same moment cannot both take the same items
        OrderRow order = loadOrder(orderId);
        List<LineRow> lines = loadLines(orderId, true);
        String problem = blocker(order);
        if (problem != null) {
            throw new ReturnException(HttpStatus.CONFLICT, problem);
        }
        BigDecimal rate = taxRate(order, lines);

        // 3. check every requested line against what is left to return
        Set<Long> seen = new HashSet<>();
        List<SalesReturnItem> toSave = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (ReturnLineRequest asked : request.items()) {
            if (asked == null || asked.orderItemId() == null || asked.quantity() == null || asked.quantity() < 1) {
                throw new ReturnException(HttpStatus.BAD_REQUEST, "Each returned item needs a quantity of 1 or more.");
            }
            if (!seen.add(asked.orderItemId())) {
                throw new ReturnException(HttpStatus.BAD_REQUEST, "The same item is listed twice.");
            }
            LineRow line = lines.stream().filter(l -> l.orderItemId().equals(asked.orderItemId())).findFirst()
                    .orElseThrow(() -> new ReturnException(HttpStatus.BAD_REQUEST,
                            "That item is not part of sale #" + orderId + "."));

            long left = line.quantity() - returnItems.totalReturnedFor(line.orderItemId());
            if (asked.quantity() > left) {
                throw new ReturnException(HttpStatus.CONFLICT, left <= 0
                        ? "Every unit of that item has already been returned."
                        : "Only " + left + " of that item can still be returned.");
            }

            BigDecimal unitRefund = refundPerUnit(line, rate);
            total = total.add(unitRefund.multiply(BigDecimal.valueOf(asked.quantity())));

            SalesReturnItem item = new SalesReturnItem();
            item.setOrderItemId(line.orderItemId());
            item.setItemId(line.itemId());
            item.setQuantity(asked.quantity());
            item.setUnitRefund(unitRefund);
            // put back on the shelf unless the staff member said no, or the reason is "damaged" and nothing was said
            item.setRestocked(asked.restock() != null ? asked.restock() : !"DAMAGED".equals(reason));
            toSave.add(item);
        }

        // never give back more than the customer paid for the items (the delivery fee is not refunded)
        BigDecimal alreadyRefunded = refundedSoFar(orderId);
        if (alreadyRefunded.add(total).subtract(order.itemsPaid()).compareTo(TOLERANCE) > 0) {
            throw new ReturnException(HttpStatus.CONFLICT, "That would refund more than the customer paid for sale #" + orderId + ".");
        }

        // 4. save the return
        SalesReturn saved = new SalesReturn();
        saved.setOrderId(orderId);
        saved.setCreatedBy(staffEmail);
        saved.setReason(reason);
        saved.setNote(note.isEmpty() ? null : note);
        saved.setRefundMethod(method);
        saved.setRefundAmount(total.setScale(2, RoundingMode.HALF_UP));
        for (SalesReturnItem item : toSave) {
            saved.addItem(item);
        }
        saved = returns.save(saved);

        // 5. stock and the ledger, the same way the sale was logged
        for (SalesReturnItem item : toSave) {
            if (Boolean.TRUE.equals(item.getRestocked())) {
                // back into the batch it was sold from (same cost, same expiry date)
                stockService.putBack(item.getOrderItemId(), item.getItemId(), item.getQuantity(), null);
            }
            logTransaction(saved, order, item, reason);
        }

        // 6. marketplace products: the seller does not keep what the customer got back
        takeBackFromSellers(saved, lines, toSave);

        return toView(saved);
    }

    /**
     * For each seller package in the return: the seller's share of the returned items (their price minus the
     * commission at the package's own rate, worked out the same way as at delivery) comes off the seller's earnings
     * as a RETURN entry. Never more than the package earned. The seller is told.
     */
    private void takeBackFromSellers(SalesReturn saved, List<LineRow> lines, List<SalesReturnItem> returned) {
        if (packages == null || ledger == null) {
            return;
        }
        Map<Long, BigDecimal> subtotalByPackage = new LinkedHashMap<>();
        Map<Long, Integer> unitsByPackage = new LinkedHashMap<>();
        for (SalesReturnItem item : returned) {
            LineRow line = lines.stream().filter(l -> l.orderItemId().equals(item.getOrderItemId())).findFirst().orElse(null);
            if (line == null || line.packageId() == null) {
                continue;
            }
            subtotalByPackage.merge(line.packageId(), line.unitPrice().multiply(BigDecimal.valueOf(item.getQuantity())), BigDecimal::add);
            unitsByPackage.merge(line.packageId(), item.getQuantity(), Integer::sum);
        }
        for (Map.Entry<Long, BigDecimal> e : subtotalByPackage.entrySet()) {
            OrderPackage p = packages.findById(e.getKey()).orElse(null);
            if (p == null || p.getSellerId() == null
                    || !ledger.existsByPackageIdAndPartyTypeAndEntryType(p.getId(), LedgerEntry.SELLER, LedgerEntry.SALE)) {
                continue; // our own products, or nothing was booked for the seller
            }
            BigDecimal subtotal = e.getValue().setScale(2, RoundingMode.HALF_UP);
            BigDecimal rate = p.getCommissionPercent() == null ? BigDecimal.ZERO : p.getCommissionPercent();
            BigDecimal commission = subtotal.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal share = subtotal.subtract(commission);
            // never take back more than the package earned, over all its returns
            BigDecimal earned = p.getSellerEarning() == null ? BigDecimal.ZERO : p.getSellerEarning();
            BigDecimal takenBefore = ledger.sumOfPackage(p.getId(), LedgerEntry.SELLER, LedgerEntry.RETURN).negate();
            share = share.min(earned.subtract(takenBefore)).max(BigDecimal.ZERO);
            if (share.signum() == 0) {
                continue;
            }
            int units = unitsByPackage.get(e.getKey());
            LedgerEntry entry = new LedgerEntry();
            entry.setPartyType(LedgerEntry.SELLER);
            entry.setPartyId(p.getSellerId());
            entry.setEntryType(LedgerEntry.RETURN);
            entry.setAmount(share.negate());
            entry.setOrderId(saved.getOrderId());
            entry.setPackageId(p.getId());
            entry.setNote("Return #" + saved.getReturnId() + ": " + units + " item(s) back from order #"
                    + saved.getOrderId() + " (" + saved.getReason().toLowerCase() + ")");
            entry.setCreatedBy(saved.getCreatedBy());
            ledger.save(entry);
            if (sellers != null && notify != null) {
                BigDecimal taken = share;
                sellers.findById(p.getSellerId()).ifPresent(seller -> notify.user(seller.getUser().getEmail(),
                        new NotificationService.Note("RETURN", "A customer returned items",
                                units + " item(s) from order #" + saved.getOrderId() + " came back. Nu. "
                                        + taken.toPlainString() + " was taken off your earnings.", "/seller"), false));
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private void logTransaction(SalesReturn saved, OrderRow order, SalesReturnItem item, String reason) {
        // RETURN = went back on the shelf. RETURN_DAMAGED = came back but is not for sale again.
        String type = Boolean.TRUE.equals(item.getRestocked()) ? "RETURN" : "RETURN_DAMAGED";
        String customer = order.customerName() == null ? "" : order.customerName();
        if (customer.length() > 100) {
            customer = customer.substring(0, 100);
        }
        // the price column holds the price of one before tax, the same as the sale rows do
        BigDecimal priceBeforeTax = orderItemPrice(order.orderId(), item.getOrderItemId());

        em.createNativeQuery(
                        "INSERT INTO transactions (created_at, customer_or_supplier, item_id, notes, quantity, reference_id, "
                                + "reference_type, transaction_type, unit_price) "
                                + "VALUES (?8, NULLIF(?1, ''), ?2, NULLIF(?3, ''), ?4, ?5, 'SALES_RETURN', ?6, ?7)")
                .setParameter(1, customer)
                .setParameter(2, item.getItemId())
                .setParameter(3, reason)
                .setParameter(4, item.getQuantity())
                .setParameter(5, saved.getReturnId())
                .setParameter(6, type)
                .setParameter(7, priceBeforeTax)
                .setParameter(8, LocalDateTime.now(java.time.ZoneOffset.UTC)) // stored in UTC, like UTC_TIMESTAMP() (works on every database)
                .executeUpdate();
    }

    @SuppressWarnings("unchecked")
    private BigDecimal orderItemPrice(Long orderId, Long orderItemId) {
        List<Object> rows = em.createNativeQuery(
                        "SELECT unit_price FROM order_items WHERE order_id = ?1 AND order_item_id = ?2")
                .setParameter(1, orderId)
                .setParameter(2, orderItemId)
                .getResultList();
        return rows.isEmpty() ? BigDecimal.ZERO : money(rows.get(0));
    }

    @SuppressWarnings("unchecked")
    private OrderRow loadOrder(Long orderId) {
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT order_id, order_status, total_amount, tax_amount, created_at, customer_name, delivery_fee "
                                + "FROM orders WHERE order_id = ?1")
                .setParameter(1, orderId)
                .getResultList();
        if (rows.isEmpty()) {
            throw new ReturnException(HttpStatus.NOT_FOUND, "Sale #" + orderId + " was not found.");
        }
        Object[] r = rows.get(0);
        return new OrderRow(((Number) r[0]).longValue(), r[1] == null ? "" : r[1].toString(), money(r[2]), money(r[3]),
                money(r[6]), time(r[4]), r[5] == null ? null : r[5].toString());
    }

    @SuppressWarnings("unchecked")
    private List<LineRow> loadLines(Long orderId, boolean lock) {
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT order_item_id, item_id, quantity, unit_price, package_id FROM order_items WHERE order_id = ?1 "
                                + "ORDER BY order_item_id" + (lock ? " FOR UPDATE" : ""))
                .setParameter(1, orderId)
                .getResultList();
        List<LineRow> lines = new ArrayList<>();
        for (Object[] r : rows) {
            lines.add(new LineRow(((Number) r[0]).longValue(), ((Number) r[1]).longValue(), ((Number) r[2]).intValue(),
                    money(r[3]), r[4] == null ? null : ((Number) r[4]).longValue()));
        }
        return lines;
    }

    private BigDecimal refundedSoFar(Long orderId) {
        Object sum = em.createNativeQuery("SELECT COALESCE(SUM(refund_amount), 0) FROM sales_returns WHERE order_id = ?1")
                .setParameter(1, orderId)
                .getSingleResult();
        return money(sum);
    }

    /** Why this sale cannot be returned right now, or null if it can. */
    private String blocker(OrderRow order) {
        if (!"COMPLETED".equalsIgnoreCase(order.status())) {
            return "Only completed sales can be returned. This one is " + order.status().toLowerCase() + ".";
        }
        long days = ChronoUnit.DAYS.between(order.createdAt(), LocalDateTime.now());
        if (days > windowDays) {
            return "This sale is " + days + " days old. Returns are accepted within " + windowDays + " days.";
        }
        return null;
    }

    private long daysLeft(OrderRow order) {
        return Math.max(0, windowDays - ChronoUnit.DAYS.between(order.createdAt(), LocalDateTime.now()));
    }

    /**
     * The sale's tax rate, worked out from the sale itself: tax / (sum of the lines).
     * The lines plus the tax must equal what the customer paid, or a fair refund cannot be worked out.
     */
    private BigDecimal taxRate(OrderRow order, List<LineRow> lines) {
        BigDecimal lineSum = BigDecimal.ZERO;
        for (LineRow line : lines) {
            lineSum = lineSum.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        // the items plus tax (plus the delivery fee of an online order) must be what the customer paid
        if (lineSum.add(order.tax()).add(order.deliveryFee()).subtract(order.total()).abs().compareTo(TOLERANCE) > 0) {
            throw new ReturnException(HttpStatus.CONFLICT, "The amounts on sale #" + order.orderId()
                    + " do not add up (the items plus tax and delivery are not the total), so a refund cannot be worked "
                    + "out automatically. Please ask the developer to look at this sale.");
        }
        return lineSum.signum() == 0 ? BigDecimal.ZERO : order.tax().divide(lineSum, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal refundPerUnit(LineRow line, BigDecimal rate) {
        return line.unitPrice().multiply(BigDecimal.ONE.add(rate)).setScale(2, RoundingMode.HALF_UP);
    }

    private ReturnView toView(SalesReturn r) {
        List<ReturnLineView> items = new ArrayList<>();
        for (SalesReturnItem i : r.getItems()) {
            items.add(new ReturnLineView(i.getOrderItemId(), i.getItemId(), i.getQuantity(), i.getUnitRefund(),
                    Boolean.TRUE.equals(i.getRestocked())));
        }
        return new ReturnView(r.getReturnId(), r.getOrderId(), r.getCreatedAt(), r.getCreatedBy(), r.getReason(),
                r.getNote(), r.getRefundMethod(), r.getRefundAmount(), items);
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }

    private static BigDecimal money(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private static LocalDateTime time(Object value) {
        if (value instanceof LocalDateTime t) {
            return t;
        }
        if (value instanceof Timestamp t) {
            return t.toLocalDateTime();
        }
        if (value instanceof java.time.Instant t) {
            return LocalDateTime.ofInstant(t, java.time.ZoneId.systemDefault());
        }
        if (value instanceof java.time.OffsetDateTime t) {
            return t.toLocalDateTime();
        }
        throw new ReturnException(HttpStatus.INTERNAL_SERVER_ERROR, "The date of the sale could not be read.");
    }
}
