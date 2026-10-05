package com.api.inventory.controller;

import com.api.inventory.service.ReceiptService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** The payment receipt of a paid order (page /receipt/{orderId}): the customer's own, or any for staff. */
@RestController
public class ReceiptController {

    private final ReceiptService receipts;

    public ReceiptController(ReceiptService receipts) {
        this.receipts = receipts;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/api/orders/{orderId}/receipt")
    public ReceiptService.Receipt receipt(@PathVariable Long orderId) {
        return receipts.receipt(orderId);
    }
}
