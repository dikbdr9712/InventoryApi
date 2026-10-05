package com.api.inventory.service;

import com.api.inventory.repository.OrderRepository;
import com.api.inventory.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Journal numbers: the number a bank or banking app shows for a transfer (also the card approval code and the UPI
 * transaction number at the counter). One rule everywhere: tidied the same way, and never accepted twice, so the same
 * transfer (or a screenshot of it) cannot pay for two orders or two counter sales.
 */
@Service
public class JournalNumbers {

    private final PaymentRepository payments;
    private final OrderRepository orders;

    public JournalNumbers(PaymentRepository payments, OrderRepository orders) {
        this.payments = payments;
        this.orders = orders;
    }

    /** Spaces trimmed and squeezed, upper case. Empty becomes null. */
    public static String tidy(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().replaceAll("\s+", " ").toUpperCase(Locale.ROOT);
        return value.isEmpty() ? null : value;
    }

    /**
     * A usable, unused journal number (tidied), or an explanation.
     * @param what how the screen calls it, for example "journal number"
     */
    public String requireNew(String raw, String what) {
        String value = tidy(raw);
        if (value == null) {
            throw new IllegalArgumentException("Enter the " + what + ".");
        }
        if (value.length() < 4 || value.length() > 40 || !value.matches("[A-Z0-9][A-Z0-9 /._-]*")) {
            throw new IllegalArgumentException("Check the " + what + ": 4 to 40 letters and digits, as shown by the bank.");
        }
        orders.findFirstByPaymentReferenceIgnoreCase(value).ifPresent(o -> {
            throw new IllegalStateException("This " + what + " was already used for sale #" + o.getOrderId() + ".");
        });
        payments.findFirstByJournalNumberIgnoreCase(value).ifPresent(p -> {
            throw new IllegalStateException("This " + what + " was already used for order #" + p.getOrderId() + ".");
        });
        return value;
    }
}
