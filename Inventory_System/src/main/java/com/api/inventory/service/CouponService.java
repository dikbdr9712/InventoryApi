package com.api.inventory.service;

import com.api.inventory.entity.Coupon;
import com.api.inventory.entity.CouponRedemption;
import com.api.inventory.repository.CouponRedemptionRepository;
import com.api.inventory.repository.CouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * Coupon codes: a percent or an amount off the items of an online order (never the delivery). The rules: switched
 * on, started and not ended, the items reach the smallest order, uses left in all and for this customer (a cancelled
 * order's use does not count). DP DrukBazaars pays for the discount: sellers earn their full share.
 */
@Service
public class CouponService {

    private final CouponRepository coupons;
    private final CouponRedemptionRepository redemptions;

    public CouponService(CouponRepository coupons, CouponRedemptionRepository redemptions) {
        this.coupons = coupons;
        this.redemptions = redemptions;
    }

    /** What a valid code takes off these items. */
    public record Quote(Long couponId, String code, String description, BigDecimal discount) {
    }

    public record CouponForm(String code, String description, String kind, BigDecimal value, BigDecimal minOrder,
                             BigDecimal maxDiscount, Instant startsAt, Instant endsAt, Integer usageLimit,
                             Integer perCustomerLimit, Boolean active) {
    }

    public record CouponView(Long id, String code, String description, String kind, BigDecimal value, BigDecimal minOrder,
                             BigDecimal maxDiscount, Instant startsAt, Instant endsAt, Integer usageLimit,
                             int perCustomerLimit, boolean active, long uses, Instant createdAt) {
    }

    /** For the cart: is the code good for these items, and how much does it take off? Throws the reason if not. */
    @Transactional(readOnly = true)
    public Quote check(String code, String email, BigDecimal itemsTotal) {
        Coupon c = coupons.findByCodeIgnoreCase(clean(code)).orElseThrow(() -> new IllegalStateException("That code does not exist."));
        return quote(c, email, itemsTotal);
    }

    /** For placing the order: the same check with the coupon locked, so its last use cannot be taken twice. */
    @Transactional
    public Quote checkForOrder(String code, String email, BigDecimal itemsTotal) {
        Coupon c = coupons.findByCodeForUpdate(clean(code)).orElseThrow(() -> new IllegalStateException("That coupon code does not exist."));
        return quote(c, email, itemsTotal);
    }

    @Transactional
    public void redeem(Quote quote, Long orderId, String email) {
        CouponRedemption r = new CouponRedemption();
        r.setCouponId(quote.couponId());
        r.setOrderId(orderId);
        r.setUserEmail(email);
        r.setAmount(quote.discount());
        r.setCreatedAt(Instant.now());
        redemptions.save(r);
    }

    private Quote quote(Coupon c, String email, BigDecimal itemsTotal) {
        Instant now = Instant.now();
        if (!c.isActive() || (c.getStartsAt() != null && now.isBefore(c.getStartsAt()))) {
            throw new IllegalStateException("The code " + c.getCode() + " is not on right now.");
        }
        if (c.getEndsAt() != null && !now.isBefore(c.getEndsAt())) {
            throw new IllegalStateException("The code " + c.getCode() + " has ended.");
        }
        BigDecimal items = itemsTotal == null ? BigDecimal.ZERO : itemsTotal;
        if (items.compareTo(c.getMinOrder()) < 0) {
            throw new IllegalStateException("The code " + c.getCode() + " needs items of at least Nu. "
                    + c.getMinOrder().setScale(2, RoundingMode.HALF_UP).toPlainString() + ".");
        }
        if (c.getUsageLimit() != null && redemptions.countUses(c.getId()) >= c.getUsageLimit()) {
            throw new IllegalStateException("The code " + c.getCode() + " has been used up.");
        }
        if (email != null && redemptions.countUsesBy(c.getId(), email) >= c.getPerCustomerLimit()) {
            throw new IllegalStateException(c.getPerCustomerLimit() == 1
                    ? "You have already used the code " + c.getCode() + "."
                    : "You have used the code " + c.getCode() + " " + c.getPerCustomerLimit() + " times already.");
        }
        BigDecimal off = Coupon.PERCENT.equals(c.getKind())
                ? items.multiply(c.getValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : c.getValue();
        if (c.getMaxDiscount() != null && off.compareTo(c.getMaxDiscount()) > 0) {
            off = c.getMaxDiscount();
        }
        off = off.min(items).setScale(2, RoundingMode.HALF_UP);
        if (off.signum() <= 0) {
            throw new IllegalStateException("The code " + c.getCode() + " takes nothing off this order.");
        }
        return new Quote(c.getId(), c.getCode(), c.getDescription(), off);
    }

    // ------------------------------------------------------------------ staff

    @Transactional(readOnly = true)
    public List<CouponView> list() {
        return coupons.findAllByOrderByCreatedAtDesc().stream().map(this::view).toList();
    }

    @Transactional
    public CouponView create(CouponForm form, String staffEmail) {
        String code = clean(form == null ? null : form.code());
        if (!code.matches("[A-Z0-9]{3,30}")) {
            throw new IllegalArgumentException("The code is 3 to 30 letters and digits, for example DRUK10.");
        }
        if (coupons.findByCodeIgnoreCase(code).isPresent()) {
            throw new IllegalStateException("The code " + code + " already exists.");
        }
        Coupon c = new Coupon();
        c.setCode(code);
        c.setCreatedBy(staffEmail);
        c.setCreatedAt(Instant.now());
        fill(c, form);
        return view(coupons.save(c));
    }

    /** Everything but the code can change. */
    @Transactional
    public CouponView update(Long id, CouponForm form) {
        Coupon c = coupons.findById(id).orElseThrow(() -> new IllegalStateException("Coupon not found."));
        fill(c, form);
        return view(coupons.save(c));
    }

    /** Only a code that was never used can be deleted; a used one is switched off instead. */
    @Transactional
    public void delete(Long id) {
        Coupon c = coupons.findById(id).orElseThrow(() -> new IllegalStateException("Coupon not found."));
        if (redemptions.countByCouponId(id) > 0) {
            throw new IllegalStateException("This code was used, so it is kept for the records. Switch it off instead.");
        }
        coupons.delete(c);
    }

    private void fill(Coupon c, CouponForm form) {
        String kind = form.kind() == null ? "" : form.kind().trim().toUpperCase();
        if (!Coupon.PERCENT.equals(kind) && !Coupon.AMOUNT.equals(kind)) {
            throw new IllegalArgumentException("Choose a percent off or an amount off.");
        }
        BigDecimal value = form.value();
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("Enter how much it takes off.");
        }
        if (Coupon.PERCENT.equals(kind) && value.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new IllegalArgumentException("A percent coupon takes at most 90% off.");
        }
        if (form.startsAt() != null && form.endsAt() != null && !form.endsAt().isAfter(form.startsAt())) {
            throw new IllegalArgumentException("The end must be after the start.");
        }
        if (form.usageLimit() != null && form.usageLimit() < 1) {
            throw new IllegalArgumentException("Uses in all: leave it empty for no limit, or 1 or more.");
        }
        int perCustomer = form.perCustomerLimit() == null ? 1 : form.perCustomerLimit();
        if (perCustomer < 1 || perCustomer > 100) {
            throw new IllegalArgumentException("Uses per customer: 1 to 100.");
        }
        String description = form.description() == null ? null : form.description().trim();
        c.setKind(kind);
        c.setValue(value.setScale(2, RoundingMode.HALF_UP));
        c.setMinOrder(form.minOrder() == null || form.minOrder().signum() < 0 ? BigDecimal.ZERO : form.minOrder().setScale(2, RoundingMode.HALF_UP));
        c.setMaxDiscount(Coupon.PERCENT.equals(kind) && form.maxDiscount() != null && form.maxDiscount().signum() > 0
                ? form.maxDiscount().setScale(2, RoundingMode.HALF_UP) : null);
        c.setStartsAt(form.startsAt());
        c.setEndsAt(form.endsAt());
        c.setUsageLimit(form.usageLimit());
        c.setPerCustomerLimit(perCustomer);
        c.setActive(form.active() == null || form.active());
        c.setDescription(description == null || description.isEmpty() ? null : description.substring(0, Math.min(200, description.length())));
    }

    private CouponView view(Coupon c) {
        return new CouponView(c.getId(), c.getCode(), c.getDescription(), c.getKind(), c.getValue(), c.getMinOrder(),
                c.getMaxDiscount(), c.getStartsAt(), c.getEndsAt(), c.getUsageLimit(), c.getPerCustomerLimit(), c.isActive(),
                redemptions.countUses(c.getId()), c.getCreatedAt());
    }

    private static String clean(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }
}
