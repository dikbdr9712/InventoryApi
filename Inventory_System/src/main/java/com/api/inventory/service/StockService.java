package com.api.inventory.service;

import com.api.inventory.entity.*;
import com.api.inventory.repository.*;
import com.api.inventory.security.CurrentUser;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Stock by batch. Every way stock moves goes through here, so the batches and the total always agree:
 *
 *   receive    a delivery becomes a new batch with its own cost, batch number and expiry date
 *   take       a sale takes from the batch that expires first (undated last, oldest first); expired stock is
 *              never sold; the sold line remembers its batches and its real cost (for profit and recalls)
 *   putBack    a return or a cancelled order goes back into the batches it came from
 *   setCount   a stock count / correction (more = a new batch, fewer = taken first-to-expire first)
 *   writeOff   damaged, lost or sent back to the supplier, always with a reason
 *   expire     every night (and before any sale of the product) batches past their date are taken off sale
 *
 * Stock that existed before batches were kept becomes one "opening" batch per product at its cost price
 * (done at start-up, see reconcile). The total stays in inventory_stock.current_quantity for the rest of the program.
 */
@Service
@Order(50)
public class StockService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    public record BatchView(Long id, Long itemId, String itemName, String sku, String batchNo, LocalDate expiryDate,
                            Long daysLeft, BigDecimal unitCost, int quantityReceived, int quantityLeft, BigDecimal value,
                            String status, String source, String supplier, Instant receivedAt, String receivedBy, String note) {
    }

    public record Summary(long units, BigDecimal stockValue, long batchesOnShelf, long expiringBatches, long expiringUnits,
                          BigDecimal expiringValue, int expiringWithinDays, long lostThisMonthUnits, BigDecimal lostThisMonthValue) {
    }

    public record ProfitReport(LocalDate from, LocalDate to, BigDecimal revenue, BigDecimal cost, BigDecimal grossProfit,
                               BigDecimal marginPercent, long unitsSold, BigDecimal returnedRevenue, BigDecimal returnedCost,
                               BigDecimal stockLosses, BigDecimal netProfit, long unitsWithoutCost) {
    }

    private final StockBatchRepository batches;
    private final OrderItemBatchRepository allocations;
    private final InventoryStockRepository stock;
    private final ItemMasterRepository items;
    private final OrderItemRepository orderItems;
    private final TransactionRepository transactions;
    private final AuditService audit;
    private final NotificationService notify;
    private final EntityManager em;
    private final ZoneId zone;
    private org.springframework.transaction.support.TransactionTemplate tx;
    private com.api.inventory.repository.SellerProfileRepository sellers;

    private org.springframework.context.ApplicationEventPublisher events;

    @org.springframework.beans.factory.annotation.Autowired
    void setEvents(org.springframework.context.ApplicationEventPublisher events) {
        this.events = events;
    }

    /** A product's stock went from nothing to some: "Notify me" alerts go out (StockAlertService). */
    public record BackInStock(Long itemId) {
    }

    /** Tells the waiting customers when this change brought a sold-out product back. */
    private void announceIfBack(Long itemId, int before) {
        Integer now = stock.quantityNow(itemId);
        if (events != null && before <= 0 && now != null && now > 0) {
            events.publishEvent(new BackInStock(itemId));
        }
    }

    private int quantityBefore(Long itemId) {
        Integer q = stock.quantityNow(itemId);
        return q == null ? 0 : q;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setSellers(com.api.inventory.repository.SellerProfileRepository sellers) {
        this.sellers = sellers;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setTransactions(org.springframework.transaction.PlatformTransactionManager transactions) {
        this.tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
    }

    public StockService(StockBatchRepository batches, OrderItemBatchRepository allocations, InventoryStockRepository stock,
                        ItemMasterRepository items, OrderItemRepository orderItems, TransactionRepository transactions,
                        AuditService audit, NotificationService notify, EntityManager em,
                        @Value("${app.timezone:Asia/Thimphu}") String timezone) {
        this.batches = batches;
        this.allocations = allocations;
        this.stock = stock;
        this.items = items;
        this.orderItems = orderItems;
        this.transactions = transactions;
        this.audit = audit;
        this.notify = notify;
        this.em = em;
        this.zone = ZoneId.of(timezone);
    }

    /** Today in the shop's time zone (an expiry date is a calendar day in Bhutan, not on the server's clock). */
    public LocalDate today() {
        return LocalDate.now(zone);
    }

    // ================= Into stock =================

    /** A delivery: a new batch. unitCost empty = the product's last known cost. */
    @Transactional
    public StockBatch receive(Long itemId, int quantity, BigDecimal unitCost, String batchNo, LocalDate expiryDate,
                              String supplier, String source, String note) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("The quantity must be at least 1.");
        }
        if (expiryDate != null && expiryDate.isBefore(today())) {
            throw new IllegalStateException("This batch expired on " + expiryDate + ". Expired stock cannot be put on sale.");
        }
        ensureStockRow(itemId);
        int before = quantityBefore(itemId);
        StockBatch b = new StockBatch();
        b.setItemId(itemId);
        b.setBatchNo(clean(batchNo, 60));
        b.setExpiryDate(expiryDate);
        b.setUnitCost(unitCost == null || unitCost.signum() < 0 ? lastCost(itemId) : unitCost.setScale(2, RoundingMode.HALF_UP));
        b.setQuantityReceived(quantity);
        b.setQuantityLeft(quantity);
        b.setSource(source == null ? StockBatch.PURCHASE : source);
        b.setSupplier(clean(supplier, 100));
        b.setReceivedAt(Instant.now());
        b.setReceivedBy(CurrentUser.email());
        b.setNote(clean(note, 300));
        StockBatch saved = batches.save(b);
        stock.adjustStockByDelta(itemId, quantity);
        stock.refreshStatus(itemId, LocalDateTime.now());
        announceIfBack(itemId, before);
        return saved;
    }

    // ================= Out of stock =================

    /** A sold line: takes its quantity first-to-expire first, and records the batches and the line's real cost. */
    @Transactional
    public void takeForLine(OrderItem line) {
        take(line.getItemId(), line.getQuantity(), line);
    }

    /** Stock taken without an order line (an old-style sale, a count that found fewer). Returns what it cost. */
    @Transactional
    public BigDecimal takeLoose(Long itemId, int quantity) {
        return take(itemId, quantity, null);
    }

    private BigDecimal take(Long itemId, int quantity, OrderItem line) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("The quantity must be at least 1.");
        }
        expireDueFor(itemId); // never sell what expired since last night
        if (stock.takeIfAvailable(itemId, quantity) == 0) {
            throw new IllegalStateException("Not enough " + nameOf(itemId) + " in stock any more. Reload and try again.");
        }
        int left = quantity;
        BigDecimal cost = BigDecimal.ZERO;
        for (StockBatch b : batches.findUsableForUpdate(itemId)) {
            if (left == 0) {
                break;
            }
            int taken = Math.min(left, b.getQuantityLeft());
            b.setQuantityLeft(b.getQuantityLeft() - taken);
            batches.save(b);
            cost = cost.add(b.getUnitCost().multiply(BigDecimal.valueOf(taken)));
            if (line != null && line.getOrderItemId() != null) {
                OrderItemBatch a = new OrderItemBatch();
                a.setOrderItemId(line.getOrderItemId());
                a.setBatchId(b.getId());
                a.setQuantity(taken);
                a.setUnitCost(b.getUnitCost());
                allocations.save(a);
            }
            left -= taken;
        }
        if (left > 0) {
            // The total allowed it but the batches do not cover it (stock added outside the program, for example by
            // hand in the database). Never stop a sale at the counter for that: book the gap as opening stock at
            // the cost price, sell it, and leave a note in the log so it can be checked.
            log.warn("Item {}: {} units sold had no batch; booked as opening stock at the cost price", itemId, left);
            StockBatch gap = new StockBatch();
            gap.setItemId(itemId);
            gap.setUnitCost(lastCost(itemId));
            gap.setQuantityReceived(left);
            gap.setQuantityLeft(0);
            gap.setSource(StockBatch.OPENING);
            gap.setReceivedAt(Instant.now());
            gap.setNote("Stock that had no batch, found during a sale (cost = the product's cost price)");
            gap = batches.save(gap);
            cost = cost.add(gap.getUnitCost().multiply(BigDecimal.valueOf(left)));
            if (line != null && line.getOrderItemId() != null) {
                OrderItemBatch a = new OrderItemBatch();
                a.setOrderItemId(line.getOrderItemId());
                a.setBatchId(gap.getId());
                a.setQuantity(left);
                a.setUnitCost(gap.getUnitCost());
                allocations.save(a);
            }
        }
        if (line != null) {
            line.setUnitCost(cost.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP));
            orderItems.save(line);
        }
        stock.refreshStatus(itemId, LocalDateTime.now());
        warnIfLow(itemId, quantity);
        return cost;
    }

    /**
     * Sold units come back on the shelf (a return, or a cancelled order), into the batches they came from.
     * A batch that has expired meanwhile takes them back as expired (not on sale). Units sold before batches
     * were kept come back as a new batch at fallbackCost.
     */
    @Transactional
    public void putBack(Long orderItemId, Long itemId, int quantity, BigDecimal fallbackCost) {
        if (quantity <= 0) {
            return;
        }
        int left = quantity;
        int before = quantityBefore(itemId);
        LocalDate today = today();
        if (orderItemId != null) {
            for (OrderItemBatch a : allocations.findByOrderItemIdOrderByIdDesc(orderItemId)) {
                int open = a.getQuantity() - a.getReturnedQuantity();
                if (left == 0 || open <= 0) {
                    continue;
                }
                int back = Math.min(left, open);
                a.setReturnedQuantity(a.getReturnedQuantity() + back);
                allocations.save(a);
                StockBatch b = batches.findByIdForUpdate(a.getBatchId()).orElse(null);
                if (b != null && !StockBatch.WRITTEN_OFF.equals(b.getStatus())) {
                    b.setQuantityLeft(b.getQuantityLeft() + back);
                    batches.save(b);
                    if (StockBatch.ACTIVE.equals(b.getStatus()) && !b.isExpiredOn(today)) {
                        stock.adjustStockByDelta(itemId, back);
                    }
                }
                left -= back;
            }
        }
        if (left > 0) {
            receive(itemId, left, fallbackCost, null, null, null, StockBatch.RETURN, "Came back from a sale made before batches were kept");
        }
        expireDueFor(itemId);
        stock.refreshStatus(itemId, LocalDateTime.now());
        announceIfBack(itemId, before);
    }

    /** A stock count: there are exactly newQuantity now. More = a new batch at unitCost (empty = last cost). */
    @Transactional
    public void setCount(Long itemId, int newQuantity, BigDecimal unitCost, String source, String note) {
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Stock cannot be below 0.");
        }
        ensureStockRow(itemId);
        expireDueFor(itemId);
        int current = Math.max(0, stock.findByItemId(itemId).map(InventoryStock::getCurrentQuantity).orElse(0));
        int diff = newQuantity - current;
        if (diff > 0) {
            receive(itemId, diff, unitCost, null, null, null, source == null ? StockBatch.ADJUSTMENT : source, note);
        } else if (diff < 0) {
            take(itemId, -diff, null);
        }
    }

    /** Part or all of a batch leaves without being sold. Always with a reason; recorded and never edited. */
    @Transactional
    public BatchView writeOff(Long batchId, int quantity, String reason) {
        StockBatch b = batches.findByIdForUpdate(batchId).orElseThrow(() -> new IllegalStateException("Batch not found."));
        String why = clean(reason, 200);
        if (why == null) {
            throw new IllegalStateException("Give a reason, for example: broken, lost, or sent back to the supplier.");
        }
        if (!StockBatch.ACTIVE.equals(b.getStatus())) {
            throw new IllegalStateException("This batch is not on sale any more.");
        }
        if (quantity <= 0 || quantity > b.getQuantityLeft()) {
            throw new IllegalStateException("Write off between 1 and " + b.getQuantityLeft() + ".");
        }
        b.setQuantityLeft(b.getQuantityLeft() - quantity);
        batches.save(b);
        lowerTotal(b.getItemId(), quantity);
        logMovement(b, "WRITE_OFF", quantity, why);
        audit.record("STOCK_WRITE_OFF", nameOf(b.getItemId()) + " batch " + label(b), quantity + " x Nu. " + b.getUnitCost() + ": " + why);
        return view(b, itemsById(List.of(b.getItemId())), today());
    }

    /** Correct a typing mistake in the batch number or expiry date. */
    @Transactional
    public BatchView updateDetails(Long batchId, String batchNo, LocalDate expiryDate) {
        StockBatch b = batches.findByIdForUpdate(batchId).orElseThrow(() -> new IllegalStateException("Batch not found."));
        String before = label(b) + " / " + (b.getExpiryDate() == null ? "no date" : b.getExpiryDate());
        b.setBatchNo(clean(batchNo, 60));
        b.setExpiryDate(expiryDate);
        batches.save(b);
        audit.record("STOCK_BATCH_CHANGED", nameOf(b.getItemId()),
                before + " -> " + label(b) + " / " + (expiryDate == null ? "no date" : expiryDate));
        expireDueFor(b.getItemId()); // a date in the past takes it off sale at once
        return view(batches.findById(batchId).orElse(b), itemsById(List.of(b.getItemId())), today());
    }

    // ================= Expiry =================

    /** Every night just after midnight: batches past their date are taken off sale, and staff are told. */
    @Scheduled(cron = "0 5 0 * * *", zone = "${app.timezone:Asia/Thimphu}")
    @Transactional
    public void expireAllDue() {
        List<StockBatch> due = batches.findDueToExpire(today());
        if (due.isEmpty()) {
            return;
        }
        long units = 0;
        BigDecimal value = BigDecimal.ZERO;
        for (StockBatch b : due) {
            units += b.getQuantityLeft();
            value = value.add(b.getUnitCost().multiply(BigDecimal.valueOf(b.getQuantityLeft())));
            expire(b);
        }
        notify.withPermission("stock.restock", new NotificationService.Note("STOCK_EXPIRED",
                due.size() + " batch(es) expired and were taken off sale",
                units + " units, Nu. " + value.setScale(2, RoundingMode.HALF_UP) + " at cost. Take them off the shelf and dispose of them safely.",
                "/admin/stock?view=expired"), true);
    }

    /** Every Monday morning: what expires within 30 days, so it can be sold first, discounted or sent back. */
    @Scheduled(cron = "0 0 8 * * MON", zone = "${app.timezone:Asia/Thimphu}")
    @Transactional(readOnly = true)
    public void warnExpiringSoon() {
        List<StockBatch> soon = batches.findExpiringBy(today().plusDays(30));
        if (soon.isEmpty()) {
            return;
        }
        long units = soon.stream().mapToLong(StockBatch::getQuantityLeft).sum();
        notify.withPermission("stock.restock", new NotificationService.Note("STOCK_EXPIRING",
                soon.size() + " batch(es) expire within 30 days",
                units + " units. Sell them first, offer a discount, or return them to the supplier.",
                "/admin/stock?view=expiring"), false);
    }

    private void expireDueFor(Long itemId) {
        LocalDate today = today();
        for (StockBatch b : batches.findUsableForUpdate(itemId)) {
            if (b.isExpiredOn(today)) {
                expire(b);
            }
        }
    }

    private void expire(StockBatch b) {
        int qty = b.getQuantityLeft();
        b.setStatus(StockBatch.EXPIRED);
        batches.save(b);
        if (qty > 0) {
            lowerTotal(b.getItemId(), qty);
            logMovement(b, "EXPIRED", qty, "Batch " + label(b) + " expired on " + b.getExpiryDate());
        }
        log.info("Batch {} of item {} expired: {} units taken off sale", b.getId(), b.getItemId(), qty);
    }

    // ================= Start-up =================

    @Override
    public void run(ApplicationArguments args) {
        // called on this object (not through Spring), so the transactions are opened here
        tx.executeWithoutResult(s -> reconcile());
        tx.executeWithoutResult(s -> expireAllDue());
    }

    /**
     * Makes the batches agree with the stock totals: a product with more stock than batches gets an "opening"
     * batch for the difference, at its cost price (this is how stock from before batches were kept is brought in).
     */
    @Transactional
    public void reconcile() {
        Map<Long, Integer> have = new HashMap<>();
        for (Object[] row : batches.activeTotals()) {
            have.put((Long) row[0], ((Number) row[1]).intValue());
        }
        int opened = 0;
        for (InventoryStock s : stock.findAll()) {
            int total = s.getCurrentQuantity() == null ? 0 : s.getCurrentQuantity();
            if (total < 0) {
                stock.setQuantity(s.getItemId(), 0);
                total = 0;
            }
            int inBatches = have.getOrDefault(s.getItemId(), 0);
            if (total > inBatches) {
                StockBatch b = new StockBatch();
                b.setItemId(s.getItemId());
                b.setUnitCost(lastCost(s.getItemId()));
                b.setQuantityReceived(total - inBatches);
                b.setQuantityLeft(total - inBatches);
                b.setSource(StockBatch.OPENING);
                b.setReceivedAt(Instant.now());
                b.setNote("Stock on hand when batches started being kept (cost = the product's cost price then)");
                batches.save(b);
                opened++;
            } else if (total < inBatches) {
                // more in batches than in total: lower the newest batches (the total is what was counted and sold)
                int extra = inBatches - total;
                List<StockBatch> newestFirst = new ArrayList<>(batches.findUsableForUpdate(s.getItemId()));
                newestFirst.sort(Comparator.comparing(StockBatch::getReceivedAt).reversed());
                for (StockBatch b : newestFirst) {
                    if (extra == 0) {
                        break;
                    }
                    int cut = Math.min(extra, b.getQuantityLeft());
                    b.setQuantityLeft(b.getQuantityLeft() - cut);
                    batches.save(b);
                    extra -= cut;
                }
                log.warn("Item {}: batches held {} more than the stock total; lowered to match", s.getItemId(), inBatches - total);
            }
        }
        if (opened > 0) {
            log.info("Opening batches made for {} products", opened);
        }
    }

    // ================= Reading =================

    /** shelf = on sale now; expiring = on sale and expiring within `days`; expired = taken off sale. */
    @Transactional(readOnly = true)
    public List<BatchView> list(String view, int days) {
        LocalDate today = today();
        List<StockBatch> list = switch (view == null ? "shelf" : view) {
            case "expiring" -> batches.findExpiringBy(today.plusDays(Math.max(1, days)));
            case "expired" -> batches.findByStatusOrderByExpiryDateDesc(StockBatch.EXPIRED);
            default -> batches.findOnShelf();
        };
        Map<Long, ItemMaster> names = itemsById(list.stream().map(StockBatch::getItemId).distinct().toList());
        return list.stream().map(b -> view(b, names, today)).toList();
    }

    @Transactional(readOnly = true)
    public List<BatchView> batchesOf(Long itemId) {
        Map<Long, ItemMaster> names = itemsById(List.of(itemId));
        LocalDate today = today();
        return batches.findByItemIdOrderByReceivedAtDesc(itemId).stream().map(b -> view(b, names, today)).toList();
    }

    @Transactional(readOnly = true)
    public Summary summary(int days) {
        LocalDate today = today();
        long units = 0;
        long onShelf = 0;
        BigDecimal value = BigDecimal.ZERO;
        for (StockBatch b : batches.findOnShelf()) {
            units += b.getQuantityLeft();
            onShelf++;
            value = value.add(b.getUnitCost().multiply(BigDecimal.valueOf(b.getQuantityLeft())));
        }
        List<StockBatch> soon = batches.findExpiringBy(today.plusDays(days));
        long soonUnits = soon.stream().mapToLong(StockBatch::getQuantityLeft).sum();
        BigDecimal soonValue = soon.stream().map(b -> b.getUnitCost().multiply(BigDecimal.valueOf(b.getQuantityLeft())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        Object[] lost = (Object[]) em.createQuery("select coalesce(sum(t.quantity), 0), coalesce(sum(t.quantity * t.unitPrice), 0) "
                        + "from Transaction t where t.transactionType in ('EXPIRED', 'WRITE_OFF') and t.createdAt >= :from")
                .setParameter("from", monthStart).getSingleResult();
        return new Summary(units, value.setScale(2, RoundingMode.HALF_UP), onShelf, soon.size(), soonUnits,
                soonValue.setScale(2, RoundingMode.HALF_UP), days, ((Number) lost[0]).longValue(), money(lost[1]));
    }

    /**
     * Profit on our own products (marketplace sellers' products are not ours) between two days:
     * what the sold lines brought in (before tax) minus what those exact units cost us, less returns,
     * and less stock that expired or was written off.
     */
    @Transactional(readOnly = true)
    public ProfitReport profit(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        String soldLines = "from OrderItem i, Order o, ItemMaster m "
                + "where o.orderId = i.orderId and m.itemId = i.itemId and m.sellerId is null and i.unitCost is not null "
                + "and o.createdAt >= :start and o.createdAt < :end and (o.orderStatus is null or o.orderStatus <> 'CANCELLED')";
        Object[] soldValue = (Object[]) em.createQuery(
                        "select coalesce(sum(i.quantity * i.unitPrice), 0), coalesce(sum(i.quantity), 0) " + soldLines)
                .setParameter("start", start).setParameter("end", end).getSingleResult();
        // the exact cost: each batch's own cost for the units taken from it (not a rounded average per line)
        Object soldCost = em.createQuery(
                        "select coalesce(sum(a.quantity * a.unitCost), 0) from OrderItemBatch a, OrderItem i, Order o, ItemMaster m "
                                + "where a.orderItemId = i.orderItemId and o.orderId = i.orderId and m.itemId = i.itemId and m.sellerId is null "
                                + "and o.createdAt >= :start and o.createdAt < :end and (o.orderStatus is null or o.orderStatus <> 'CANCELLED')")
                .setParameter("start", start).setParameter("end", end).getSingleResult();
        Object[] sold = new Object[] {soldValue[0], soldCost, soldValue[1]};
        Number noCost = (Number) em.createQuery(
                        "select coalesce(sum(i.quantity), 0) from OrderItem i, Order o, ItemMaster m "
                                + "where o.orderId = i.orderId and m.itemId = i.itemId and m.sellerId is null and i.unitCost is null "
                                + "and o.createdAt >= :start and o.createdAt < :end and o.orderStatus in ('CONFIRMED', 'SHIPPED', 'COMPLETED')")
                .setParameter("start", start).setParameter("end", end).getSingleResult();
        Instant rStart = start.atZone(zone).toInstant();
        Instant rEnd = end.atZone(zone).toInstant();
        Object[] returned = (Object[]) em.createQuery(
                        "select coalesce(sum(r.quantity * i.unitPrice), 0), "
                                + "coalesce(sum(case when r.restocked = true then r.quantity * i.unitCost else 0 end), 0) "
                                + "from SalesReturnItem r join r.salesReturn s, OrderItem i, ItemMaster m "
                                + "where i.orderItemId = r.orderItemId and m.itemId = i.itemId and m.sellerId is null and i.unitCost is not null "
                                + "and s.createdAt >= :start and s.createdAt < :end")
                .setParameter("start", rStart).setParameter("end", rEnd).getSingleResult();
        Object[] lost = (Object[]) em.createQuery("select coalesce(sum(t.quantity * t.unitPrice), 0), 0 from Transaction t "
                        + "where t.transactionType in ('EXPIRED', 'WRITE_OFF') and t.createdAt >= :start and t.createdAt < :end")
                .setParameter("start", start).setParameter("end", end).getSingleResult();

        BigDecimal revenue = money(sold[0]);
        BigDecimal cost = money(sold[1]);
        BigDecimal gross = revenue.subtract(cost);
        BigDecimal returnedRevenue = money(returned[0]);
        BigDecimal returnedCost = money(returned[1]);
        BigDecimal losses = money(lost[0]);
        BigDecimal net = gross.subtract(returnedRevenue).add(returnedCost).subtract(losses);
        BigDecimal netRevenue = revenue.subtract(returnedRevenue);
        BigDecimal margin = netRevenue.signum() > 0
                ? net.multiply(BigDecimal.valueOf(100)).divide(netRevenue, 1, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        return new ProfitReport(from, to, revenue, cost, gross, margin, ((Number) sold[2]).longValue(), returnedRevenue,
                returnedCost, losses, net, noCost == null ? 0 : noCost.longValue());
    }

    // ================= Helpers =================

    private void ensureStockRow(Long itemId) {
        if (stock.findByItemId(itemId).isEmpty()) {
            InventoryStock s = new InventoryStock();
            s.setItemId(itemId);
            s.setCurrentQuantity(0);
            s.setStatus("Unavailable");
            s.setLastUpdated(LocalDateTime.now());
            stock.save(s);
        }
    }

    /** Lowers the total by quantity, never below 0. */
    private void lowerTotal(Long itemId, int quantity) {
        if (stock.takeIfAvailable(itemId, quantity) == 0) {
            int now = stock.findByItemId(itemId).map(InventoryStock::getCurrentQuantity).orElse(0);
            stock.setQuantity(itemId, Math.max(0, now - quantity));
        }
        stock.refreshStatus(itemId, LocalDateTime.now());
        warnIfLow(itemId, quantity);
    }

    /**
     * The stock just went down by "taken". When that brings it to the product's warning level (or below) from above
     * it, the people who restock are told, once; for a seller's product, the seller. Sold out says so.
     */
    private void warnIfLow(Long itemId, int taken) {
        ItemMaster item = items.findById(itemId).orElse(null);
        if (item == null || item.getLowStockThreshold() == null) {
            return;
        }
        Integer now = stock.quantityNow(itemId);
        int after = now == null ? 0 : Math.max(0, now);
        int level = item.getLowStockThreshold();
        if (after > level || after + taken <= level) {
            return; // still above the level, or it was already low before
        }
        String name = item.getItemName();
        NotificationService.Note note = after == 0
                ? new NotificationService.Note("LOW_STOCK", name + " is sold out", "No " + name + " left. Restock it soon.",
                        item.getSellerId() == null ? "/restock" : "/seller")
                : new NotificationService.Note("LOW_STOCK", "Low stock: " + name,
                        "Only " + after + " left (you asked to be warned at " + level + ").",
                        item.getSellerId() == null ? "/restock" : "/seller");
        if (item.getSellerId() == null) {
            notify.withPermission("stock.restock", note, false);
        } else if (sellers != null) {
            sellers.findById(item.getSellerId()).ifPresent(s -> notify.user(s.getUser().getEmail(), note, false));
        }
    }

    public record LowStock(Long itemId, String itemName, String sku, int quantity, int warnAt, Long sellerId) {
    }

    /** Products at or below their warning level, the emptiest first. */
    @Transactional(readOnly = true)
    public List<LowStock> lowStock() {
        Map<Long, Integer> quantities = new HashMap<>();
        stock.findAll().forEach(s -> quantities.put(s.getItemId(), s.getCurrentQuantity() == null ? 0 : s.getCurrentQuantity()));
        return items.findAll().stream()
                .filter(i -> i.getLowStockThreshold() != null && !Boolean.FALSE.equals(i.getIsActive()))
                .filter(i -> quantities.getOrDefault(i.getItemId(), 0) <= i.getLowStockThreshold())
                .map(i -> new LowStock(i.getItemId(), i.getItemName(), i.getSku(), quantities.getOrDefault(i.getItemId(), 0),
                        i.getLowStockThreshold(), i.getSellerId()))
                .sorted(java.util.Comparator.comparingInt(LowStock::quantity).thenComparing(LowStock::itemName))
                .toList();
    }

    private void logMovement(StockBatch b, String type, int quantity, String note) {
        Transaction t = new Transaction();
        t.setItemId(b.getItemId());
        t.setTransactionType(type);
        t.setQuantity(quantity);
        t.setUnitPrice(b.getUnitCost());
        t.setNotes(clean(note, 250));
        t.setReferenceId(b.getId());
        t.setReferenceType("STOCK_BATCH");
        t.setCreatedAt(LocalDateTime.now());
        transactions.save(t);
    }

    private BigDecimal lastCost(Long itemId) {
        return items.findById(itemId).map(ItemMaster::getCostPrice).filter(c -> c != null && c.signum() >= 0)
                .map(c -> c.setScale(2, RoundingMode.HALF_UP)).orElse(BigDecimal.ZERO.setScale(2));
    }

    private String nameOf(Long itemId) {
        return items.findById(itemId).map(ItemMaster::getItemName).orElse("item " + itemId);
    }

    private Map<Long, ItemMaster> itemsById(Collection<Long> ids) {
        Map<Long, ItemMaster> map = new HashMap<>();
        items.findAllById(ids).forEach(i -> map.put(i.getItemId(), i));
        return map;
    }

    private BatchView view(StockBatch b, Map<Long, ItemMaster> names, LocalDate today) {
        ItemMaster item = names.get(b.getItemId());
        Long daysLeft = b.getExpiryDate() == null ? null : ChronoUnit.DAYS.between(today, b.getExpiryDate());
        return new BatchView(b.getId(), b.getItemId(), item == null ? "Item #" + b.getItemId() : item.getItemName(),
                item == null ? null : item.getSku(), b.getBatchNo(), b.getExpiryDate(), daysLeft, b.getUnitCost(),
                b.getQuantityReceived(), b.getQuantityLeft(), b.getUnitCost().multiply(BigDecimal.valueOf(b.getQuantityLeft())),
                b.getStatus(), b.getSource(), b.getSupplier(), b.getReceivedAt(), b.getReceivedBy(), b.getNote());
    }

    private static String label(StockBatch b) {
        return b.getBatchNo() == null ? "#" + b.getId() : b.getBatchNo();
    }

    private static BigDecimal money(Object value) {
        BigDecimal v = value instanceof BigDecimal d ? d : new BigDecimal(String.valueOf(value == null ? 0 : value));
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        return v.length() <= max ? v : v.substring(0, max);
    }
}
