package com.api.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** The shapes of the data sent to and from the returns endpoints. */
public final class ReturnDTO {

    private ReturnDTO() {
    }

    /** One line the staff member wants to take back. restock = put it back on the shelf (default: yes). */
    public record ReturnLineRequest(Long orderItemId, Integer quantity, Boolean restock) {
    }

    /** Body of POST /api/orders/{orderId}/returns */
    public record ReturnRequest(String reason, String note, String refundMethod, List<ReturnLineRequest> items) {
    }

    /** One line of a sale, as the Return dialog needs it. unitRefund = what the customer really paid for one (tax included). */
    public record ReturnableLine(Long orderItemId, Long itemId, int quantitySold, long quantityReturned,
                                 long quantityLeft, BigDecimal unitRefund) {
    }

    /** Answer of GET /api/orders/{orderId}/returnable. eligible = false means message says why not. */
    public record ReturnableOrder(Long orderId, String orderStatus, boolean eligible, String message, long daysLeft,
                                  BigDecimal paidTotal, BigDecimal refundedSoFar, List<ReturnableLine> lines) {
    }

    public record ReturnLineView(Long orderItemId, Long itemId, int quantity, BigDecimal unitRefund,
                                 boolean restocked) {
    }

    /** A return that has been recorded. */
    public record ReturnView(Long returnId, Long orderId, Instant createdAt, String createdBy, String reason,
                             String note, String refundMethod, BigDecimal refundAmount, List<ReturnLineView> items) {
    }
}
