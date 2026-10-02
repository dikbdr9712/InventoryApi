package com.api.inventory.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.api.inventory.dto.PaymentRequestDTO;
import com.api.inventory.entity.Payment;
import com.api.inventory.service.PaymentService;
import com.api.inventory.service.OrderService;
import com.api.inventory.security.OrderAccess;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderAccess orderAccess;

    // ✅ Create a new payment (called after user selects payment method)
    // Only the customer who placed the order may pay for it (no staff permission is passed on purpose).
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Payment> createPayment(@RequestBody PaymentRequestDTO dto) {
        if (dto == null || dto.getOrderId() == null) {
            throw new IllegalStateException("Order number is missing.");
        }
        orderAccess.requireOwnerOrPermission(dto.getOrderId());
        Payment payment = paymentService.createPayment(dto);
        return ResponseEntity.ok(payment);
    }

    // ✅ Update payment status (optional: if you want to manually update later)
    @PutMapping("/{paymentId}/status")
    @PreAuthorize("hasAuthority('payments.verify')")
    public ResponseEntity<Payment> updatePaymentStatus(
            @PathVariable Long paymentId,
            @RequestParam String status) {
        Payment updatedPayment = paymentService.updatePaymentStatus(paymentId, status);
        return ResponseEntity.ok(updatedPayment);
    }

    // ✅ Get payment by order ID (useful for admin or customer view)
    @GetMapping("/order/{orderId}")

    @PreAuthorize("hasAnyAuthority('payments.verify','orders.view')")
    public ResponseEntity<Payment> getPaymentByOrderId(@PathVariable Long orderId) {
        System.out.println("Fetching payment for order ID: " + orderId);
        Payment payment = paymentService.findByOrderId(orderId);
        if (payment != null) {
            System.out.println("Found payment: " + payment);
            return ResponseEntity.ok(payment);
        } else {
            System.out.println("No payment found for order ID: " + orderId);
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{paymentId}/confirm") // ← Fixed path (removed duplicate /payments/)
    @PreAuthorize("hasAuthority('payments.verify')")
    public ResponseEntity<?> confirmPayment(@PathVariable Long paymentId) {
        try {
            // Get payment
            Payment payment = paymentService.findById(paymentId);
            if (!"pending".equals(payment.getStatus())) {
                return ResponseEntity.badRequest().body("Payment already processed");
            }

            // Update payment status
            payment.setStatus("confirmed");
            paymentService.save(payment);

            // ✅ Update order's payment_status
            orderService.updatePaymentStatus(payment.getOrderId(), "PAID");

            return ResponseEntity.ok("Payment confirmed and order updated");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}