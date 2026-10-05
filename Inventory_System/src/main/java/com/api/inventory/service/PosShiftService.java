package com.api.inventory.service;

import com.api.inventory.entity.Order;
import com.api.inventory.entity.PosShift;
import com.api.inventory.entity.SalesReturn;
import com.api.inventory.entity.User;
import com.api.inventory.repository.OrderRepository;
import com.api.inventory.repository.PosShiftRepository;
import com.api.inventory.repository.SalesReturnRepository;
import com.api.inventory.repository.UserRepository;
import com.api.inventory.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Cash drawer shifts. A cashier opens a shift (with the cash float in the drawer), sells, and at the end counts the
 * drawer. The report says how much cash SHOULD be there, so a shortage is seen the same day.
 */
@Service
public class PosShiftService {

    private static final BigDecimal MAX_FLOAT = new BigDecimal("1000000");

    private final PosShiftRepository shifts;
    private final OrderRepository orders;
    private final SalesReturnRepository returns;
    private final UserRepository users;
    private final AuditService audit;

    public PosShiftService(PosShiftRepository shifts, OrderRepository orders, SalesReturnRepository returns,
                           UserRepository users, AuditService audit) {
        this.shifts = shifts;
        this.orders = orders;
        this.returns = returns;
        this.users = users;
        this.audit = audit;
    }

    /** Everything about one shift, worked out from its sales (live while open, frozen once closed). */
    public record ShiftReport(Long id, String status, String cashierEmail, String cashierName,
                              Instant openedAt, Instant closedAt, String closedBy,
                              BigDecimal openingFloat, int saleCount, BigDecimal totalSales,
                              Map<String, BigDecimal> salesByMethod, BigDecimal cashSales, BigDecimal otherSales,
                              BigDecimal cashRefunds, BigDecimal discounts, BigDecimal tax,
                              BigDecimal expectedCash, BigDecimal countedCash, BigDecimal difference, String closingNote,
                              List<NonCashSale> nonCashSales) {
    }

    /** A sale paid without cash, with its journal number: to match against the bank statement at closing. */
    public record NonCashSale(Long orderId, String method, String reference, BigDecimal amount, java.time.LocalDateTime at) {
    }

    public record OpenRequest(BigDecimal openingFloat) {
    }

    public record CloseRequest(BigDecimal countedCash, String note) {
    }

    /** The signed-in cashier's open shift, if any. */
    public Optional<PosShift> openShiftOf(String email) {
        return shifts.findFirstByCashierEmailAndStatus(email, PosShift.OPEN);
    }

    /** Used by every counter sale: no open drawer, no sale. */
    public PosShift requireOpenShift() {
        return openShiftOf(CurrentUser.email())
                .orElseThrow(() -> new IllegalStateException("Open your cash drawer (start a shift) before selling."));
    }

    @Transactional
    public ShiftReport open(OpenRequest request) {
        String email = CurrentUser.email();
        if (openShiftOf(email).isPresent()) {
            throw new IllegalStateException("You already have an open shift.");
        }
        BigDecimal opening = request == null || request.openingFloat() == null ? BigDecimal.ZERO : request.openingFloat();
        if (opening.signum() < 0 || opening.compareTo(MAX_FLOAT) > 0) {
            throw new IllegalStateException("Enter the cash in the drawer (0 or more).");
        }
        PosShift shift = new PosShift();
        shift.setCashierEmail(email);
        shift.setCashierName(users.findByEmail(email).map(User::getName).orElse(email));
        shift.setOpenedAt(Instant.now());
        shift.setOpeningFloat(opening.setScale(2, RoundingMode.HALF_UP));
        shift.setStatus(PosShift.OPEN);
        PosShift saved = shifts.save(shift);
        audit.record("SHIFT_OPENED", "shift " + saved.getId(), "Float Nu. " + saved.getOpeningFloat());
        return report(saved);
    }

    /** Count the drawer and close. Your own shift, or anyone's with pos.shifts.manage (for a drawer left open). */
    @Transactional
    public ShiftReport close(Long shiftId, CloseRequest request) {
        PosShift shift = shifts.findByIdForUpdate(shiftId).orElseThrow(() -> new IllegalStateException("Shift not found."));
        String me = CurrentUser.email();
        if (!shift.getCashierEmail().equalsIgnoreCase(me) && !CurrentUser.has("pos.shifts.manage")) {
            throw new AccessDeniedException("You can only close your own cash drawer.");
        }
        if (!PosShift.OPEN.equals(shift.getStatus())) {
            throw new IllegalStateException("This shift is already closed.");
        }
        if (request == null || request.countedCash() == null || request.countedCash().signum() < 0) {
            throw new IllegalStateException("Count the cash in the drawer and enter the amount.");
        }
        String note = request.note() == null ? null : request.note().trim();

        ShiftReport live = report(shift);
        BigDecimal counted = request.countedCash().setScale(2, RoundingMode.HALF_UP);
        BigDecimal difference = counted.subtract(live.expectedCash());
        if (difference.signum() != 0 && (note == null || note.isEmpty())) {
            throw new IllegalStateException("The count is " + (difference.signum() < 0 ? "short" : "over") + " by Nu. "
                    + difference.abs() + ". Please write a short note about it.");
        }

        shift.setStatus(PosShift.CLOSED);
        shift.setClosedAt(Instant.now());
        shift.setClosedBy(me);
        shift.setSaleCount(live.saleCount());
        shift.setCashSales(live.cashSales());
        shift.setOtherSales(live.otherSales());
        shift.setCashRefunds(live.cashRefunds());
        shift.setExpectedCash(live.expectedCash());
        shift.setCountedCash(counted);
        shift.setDifference(difference);
        shift.setClosingNote(note == null || note.isEmpty() ? null : note.substring(0, Math.min(500, note.length())));
        shifts.save(shift);
        audit.record("SHIFT_CLOSED", "shift " + shift.getId() + " (" + shift.getCashierEmail() + ")",
                "Expected Nu. " + live.expectedCash() + ", counted Nu. " + counted + ", difference Nu. " + difference);
        return report(shift);
    }

    public Optional<ShiftReport> myCurrent() {
        return openShiftOf(CurrentUser.email()).map(this::report);
    }

    public List<ShiftReport> list() {
        List<PosShift> rows = CurrentUser.has("pos.shifts.manage")
                ? shifts.findTop200ByOrderByIdDesc()
                : shifts.findTop100ByCashierEmailOrderByIdDesc(CurrentUser.email());
        return rows.stream().map(this::report).toList();
    }

    public ShiftReport get(Long id) {
        PosShift shift = shifts.findById(id).orElseThrow(() -> new IllegalStateException("Shift not found."));
        if (!shift.getCashierEmail().equalsIgnoreCase(CurrentUser.email()) && !CurrentUser.has("pos.shifts.manage")) {
            throw new AccessDeniedException("This is not your shift.");
        }
        return report(shift);
    }

    // ---------- the numbers ----------

    ShiftReport report(PosShift shift) {
        int count = 0;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal discounts = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        Map<String, BigDecimal> byMethod = new TreeMap<>();
        List<NonCashSale> nonCash = new ArrayList<>();
        for (Order o : orders.findByShiftId(shift.getId())) {
            if ("CANCELLED".equalsIgnoreCase(o.getOrderStatus())) {
                continue;
            }
            count++;
            BigDecimal amount = nz(o.getTotalAmount());
            total = total.add(amount);
            discounts = discounts.add(nz(o.getDiscountAmount()));
            tax = tax.add(nz(o.getTaxAmount()));
            String method = o.getPaymentMethod() == null ? "OTHER" : o.getPaymentMethod().trim().toUpperCase();
            byMethod.merge(method, amount, BigDecimal::add);
            if (!"CASH".equals(method)) {
                nonCash.add(new NonCashSale(o.getOrderId(), method, o.getPaymentReference(), amount, o.getCreatedAt()));
            }
        }
        nonCash.sort(Comparator.comparing(NonCashSale::orderId));
        BigDecimal cash = byMethod.getOrDefault("CASH", BigDecimal.ZERO);
        BigDecimal other = total.subtract(cash);

        // cash given back for returns this cashier took during the shift
        Instant to = shift.getClosedAt() == null ? Instant.now() : shift.getClosedAt();
        BigDecimal refunds = BigDecimal.ZERO;
        for (SalesReturn r : returns.findByCreatedByAndCreatedAtBetween(shift.getCashierEmail(), shift.getOpenedAt(), to)) {
            if (r.getRefundMethod() == null || "CASH".equalsIgnoreCase(r.getRefundMethod().trim())) {
                refunds = refunds.add(nz(r.getRefundAmount()));
            }
        }

        boolean closed = PosShift.CLOSED.equals(shift.getStatus());
        BigDecimal expected = closed && shift.getExpectedCash() != null ? shift.getExpectedCash()
                : shift.getOpeningFloat().add(cash).subtract(refunds).setScale(2, RoundingMode.HALF_UP);

        return new ShiftReport(shift.getId(), shift.getStatus(), shift.getCashierEmail(), shift.getCashierName(),
                shift.getOpenedAt(), shift.getClosedAt(), shift.getClosedBy(), shift.getOpeningFloat(),
                closed && shift.getSaleCount() != null ? shift.getSaleCount() : count,
                total.setScale(2, RoundingMode.HALF_UP), byMethod,
                closed && shift.getCashSales() != null ? shift.getCashSales() : cash.setScale(2, RoundingMode.HALF_UP),
                closed && shift.getOtherSales() != null ? shift.getOtherSales() : other.setScale(2, RoundingMode.HALF_UP),
                closed && shift.getCashRefunds() != null ? shift.getCashRefunds() : refunds.setScale(2, RoundingMode.HALF_UP),
                discounts.setScale(2, RoundingMode.HALF_UP), tax.setScale(2, RoundingMode.HALF_UP),
                expected, shift.getCountedCash(), shift.getDifference(), shift.getClosingNote(), nonCash);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
