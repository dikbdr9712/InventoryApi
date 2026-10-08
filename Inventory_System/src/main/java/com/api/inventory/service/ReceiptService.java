package com.api.inventory.service;

import com.api.inventory.dto.OrderItemResponseDTO;
import com.api.inventory.entity.BankPayment;
import com.api.inventory.entity.Order;
import com.api.inventory.entity.Payment;
import com.api.inventory.entity.TaxDetail;
import com.api.inventory.repository.BankPaymentRepository;
import com.api.inventory.repository.PaymentRepository;
import com.api.inventory.repository.UserRepository;
import com.api.inventory.security.OrderAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The payment receipt of a paid order: online orders (bank transfer, or paid from a bank account with a code) and
 * counter sales alike. Everything comes from the server's records, including the journal number of the payment.
 * The customer sees their own orders; staff who see orders or use the till see any.
 */
@Service
public class ReceiptService {

    private static final Map<String, String> COUNTER_METHODS = Map.of(
            "CASH", "Cash", "CARD", "Card", "UPI", "UPI", "BANK_TRANSFER", "Bank transfer (mobile banking)");

    public record Line(String name, int quantity, BigDecimal unitPrice, BigDecimal amount) {
    }

    public record TaxLine(String label, BigDecimal amount) {
    }

    /**
     * How it was paid. journal: the journal number (bank transfer / bank payment) or reference (card, UPI);
     * account: "Bank of Bhutan, account ending 4321" for payments from a bank account.
     */
    public record PaymentInfo(String method, String journal, String journalLabel, String account, LocalDateTime paidAt,
                              BigDecimal cashReceived, BigDecimal change) {
    }

    public record Receipt(Long orderId, String source, LocalDateTime orderedAt, String customerName, String customerPhone,
                          String customerEmail, String address, String servedBy, List<Line> lines, BigDecimal itemsTotal,
                          BigDecimal savings, List<TaxLine> taxes, BigDecimal deliveryFee, BigDecimal total, PaymentInfo payment,
                          boolean pickup, // pickup: the customer collected it (no delivery)
                          String couponCode, BigDecimal couponDiscount) { // a coupon taken off the items (online orders)
    }

    private final OrderAccess orderAccess;
    private final OrderService orders;
    private final PaymentRepository payments;
    private final BankPaymentRepository bankPayments;
    private final UserRepository users;

    public ReceiptService(OrderAccess orderAccess, OrderService orders, PaymentRepository payments,
                          BankPaymentRepository bankPayments, UserRepository users) {
        this.orderAccess = orderAccess;
        this.orders = orders;
        this.payments = payments;
        this.bankPayments = bankPayments;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Receipt receipt(Long orderId) {
        Order order = orderAccess.requireOwnerOrPermission(orderId, "orders.view", "pos.use");
        if (!"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new IllegalStateException("The receipt is ready once the payment of order #" + orderId + " is confirmed.");
        }

        List<Line> lines = new ArrayList<>();
        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (OrderItemResponseDTO item : orders.getOrderItemsByOrderId(orderId)) {
            int qty = item.getQuantity() == null ? 0 : item.getQuantity();
            BigDecimal unit = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
            BigDecimal amount = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            itemsTotal = itemsTotal.add(amount);
            lines.add(new Line(item.getItemName() == null ? "Item" : item.getItemName(), qty, unit, amount));
        }

        List<TaxLine> taxes = new ArrayList<>();
        if (order.getTaxDetails() != null) {
            for (TaxDetail t : order.getTaxDetails()) {
                if (t.getAmount() != null && t.getAmount().signum() > 0) {
                    String rate = t.getRate() == null ? "" : " " + t.getRate().stripTrailingZeros().toPlainString() + "%";
                    taxes.add(new TaxLine((t.getTaxType() == null ? "Tax" : t.getTaxType()) + rate, t.getAmount()));
                }
            }
        }

        boolean counter = "POS".equalsIgnoreCase(order.getSource());
        String servedBy = null;
        if (counter && order.getCashier() != null) {
            servedBy = users.findByEmail(order.getCashier()).map(u -> u.getName()).orElse(order.getCashier());
        }
        BigDecimal savings = order.getDiscountAmount() != null && order.getDiscountAmount().signum() > 0 ? order.getDiscountAmount() : null;

        return new Receipt(order.getOrderId(), counter ? "POS" : "ONLINE", order.getCreatedAt(), order.getCustomerName(),
                order.getCustomerPhone(), counter ? null : order.getCustomerEmail(), counter ? null : order.getAddress(), servedBy,
                lines, itemsTotal, savings, taxes, counter ? null : order.getDeliveryFee(), order.getTotalAmount(),
                counter ? counterPayment(order) : onlinePayment(order), !counter && order.isPickup(),
                counter ? null : order.getCouponCode(), counter ? null : order.getCouponDiscount());
    }

    private static PaymentInfo counterPayment(Order order) {
        String method = order.getPaymentMethod() == null ? "" : order.getPaymentMethod().trim().toUpperCase(Locale.ROOT);
        BigDecimal received = "CASH".equals(method) ? order.getAmountTendered() : null;
        BigDecimal change = received == null || order.getTotalAmount() == null ? null
                : received.subtract(order.getTotalAmount()).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        String label = switch (method) {
            case "BANK_TRANSFER" -> "Journal no.";
            case "CARD" -> "Approval code";
            default -> "Transaction no.";
        };
        return new PaymentInfo(COUNTER_METHODS.getOrDefault(method, method.isEmpty() ? "Not recorded" : method),
                order.getPaymentReference(), label, null, order.getCreatedAt(), received, change);
    }

    private PaymentInfo onlinePayment(Order order) {
        Payment p = payments.findByOrderId(order.getOrderId()).orElse(null);
        String journal = p == null ? null : p.getJournalNumber();
        String method = "Bank transfer";
        String account = null;
        if (p != null && "online".equalsIgnoreCase(p.getPaymentMethod())) {
            // recorded as "ONLINE <gateway> <journal>" by OnlinePaymentService
            String[] parts = journal == null ? new String[0] : journal.split(" ", 3);
            String gateway = parts.length >= 2 ? parts[1] : "";
            journal = parts.length == 3 ? parts[2] : journal;
            if ("BANK".equals(gateway)) {
                method = "Bank account (RMA Payment Gateway)";
                BankPayment bp = bankPayments.findFirstByOrderIdAndStatusOrderByIdDesc(order.getOrderId(), BankPayment.PAID).orElse(null);
                if (bp != null) {
                    account = bp.getBankName() + ", account ending " + bp.getAccountLast4();
                }
            } else {
                method = "Online payment" + ("SANDBOX".equals(gateway) ? " (test)" : "");
            }
        }
        return new PaymentInfo(method, journal, "Journal no.", account, p == null ? order.getUpdatedAt() : p.getPaymentDate(), null, null);
    }
}
