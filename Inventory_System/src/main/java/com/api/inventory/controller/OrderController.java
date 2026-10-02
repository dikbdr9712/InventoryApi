package com.api.inventory.controller;

import com.api.inventory.security.OrderAccess;
import com.api.inventory.dto.OrderItemResponseDTO;
import com.api.inventory.dto.OrderRequestDTO;
import com.api.inventory.dto.OrderResponseDTO;
import com.api.inventory.dto.OrderVerificationDTO;
import com.api.inventory.dto.PosSaleRequestDTO;
import com.api.inventory.dto.ShipmentRequestDTO;
import com.api.inventory.dto.VerificationRequestDTO;
import com.api.inventory.entity.ItemMaster;
import com.api.inventory.entity.Order;
import com.api.inventory.entity.OrderItem;
import com.api.inventory.entity.Payment;
import com.api.inventory.exception.ResourceNotFoundException;
import com.api.inventory.repository.ItemMasterRepository;
import com.api.inventory.repository.OrderItemRepository;
import com.api.inventory.service.EmailService;
import com.api.inventory.service.OrderService;
import com.api.inventory.service.PaymentService;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private EmailService emailService;
    @Autowired
    private OrderAccess orderAccess;
    @Autowired
    private com.api.inventory.service.PackageService packageService;

    // Keep your existing repositories if used elsewhere (e.g., in createOrder logic)
    // If not used, you can remove these — but keeping for safety
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private ItemMasterRepository itemMasterRepository;

    @PostMapping("/pos/sale")
    @PreAuthorize("hasAuthority('pos.use')")
    public ResponseEntity<Order> createInPersonSale(@RequestBody PosSaleRequestDTO request) {
        Order order = orderService.createInPersonSale(request);
        return ResponseEntity.ok(order);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Order createOrder(@RequestBody OrderRequestDTO dto) {
        orderAccess.requireOwnEmail(dto.getCustomerEmail());
        return orderService.createOrder(dto);
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public void cancelOrder(@PathVariable Long orderId) {
        orderAccess.requireCancellable(orderId);
        orderService.cancelOrder(orderId);
    }

    @GetMapping("/customer/{email}")
    @PreAuthorize("isAuthenticated()")
    public List<OrderResponseDTO> getOrdersByCustomer(@PathVariable String email) {
        orderAccess.requireSelfOrPermission(email, "orders.view");
        List<Order> orders = orderService.getOrdersByCustomerEmail(email);
        return orders.stream()
                .map(OrderResponseDTO::fromEntity)
                .toList();
    }

    @GetMapping("/{orderId}/items")
    @PreAuthorize("isAuthenticated()")
    public List<OrderItemResponseDTO> getOrderItems(@PathVariable Long orderId) {
        orderAccess.requireOwnerOrPermission(orderId, "orders.view", "pos.use");
        return orderService.getOrderItemsByOrderId(orderId);
    }

    // ✅ FIXED: Now returns OrderResponseDTO instead of Order
    /** Where each part of the order is. The customer also gets the 4-digit code to give the rider. */
    @GetMapping("/{orderId}/packages")
    @PreAuthorize("isAuthenticated()")
    public List<com.api.inventory.dto.MarketplaceDTOs.PackageView> getOrderPackages(@PathVariable Long orderId) {
        Order order = orderAccess.requireOwnerOrPermission(orderId, "orders.view");
        return packageService.forOrder(orderId, orderAccess.isOwner(order));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public OrderResponseDTO getOrderById(@PathVariable Long orderId) {
        Order order = orderAccess.requireOwnerOrPermission(orderId, "orders.view", "pos.use");
        return OrderResponseDTO.fromEntity(order);
    }

    // ⚠️ Note: This path overlaps with base /api/orders — consider removing "/orders"
    @PostMapping("/{orderId}/confirm-payment")
    @PreAuthorize("hasAuthority('payments.verify')")
    public ResponseEntity<Void> confirmPayment(@PathVariable Long orderId) {
        orderService.confirmPayment(orderId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{orderId}/verify")
    @PreAuthorize("hasAuthority('payments.verify')")
    public ResponseEntity<Order> verifyOrder(
            @PathVariable Long orderId,
            @RequestBody VerificationRequestDTO request) {

        String updatedBy = getCurrentUserEmail();
        System.out.println(">>> Updating order by: " + updatedBy);

        // Only known values: this endpoint must never jump an order to SHIPPED/COMPLETED and skip delivery
        if (request.getStatus() != null && !java.util.Set.of("CONFIRMED", "CANCELLED").contains(request.getStatus())) {
            throw new IllegalStateException("Unknown order status: " + request.getStatus());
        }
        if (request.getPaymentStatus() != null && !java.util.Set.of("PAID", "PARTIALLY_PAID", "PENDING_INFO", "REJECTED", "FAILED", "PENDING")
                .contains(request.getPaymentStatus().trim().toUpperCase())) {
            throw new IllegalStateException("Unknown payment status: " + request.getPaymentStatus());
        }
        if ("CANCELLED".equals(request.getStatus())) {
            orderService.cancelOrder(orderId); // gives stock back and cancels the packages
        }

        Order order = orderService.findById(orderId);

        // Marking a new order as fully paid also confirms it: stock is taken and the sellers can start packing
        boolean paidNow = request.getPaymentStatus() != null && "PAID".equalsIgnoreCase(request.getPaymentStatus().trim())
                && request.getStatus() == null && "CREATED".equals(order.getOrderStatus());
        boolean confirmedNow = false;
        if (paidNow) {
            order.setPaymentStatus("PAID");
            orderService.save(order);
            orderService.processOrderConfirmation(orderId); // takes stock once
            order = orderService.findById(orderId);
            order.setOrderStatus("CONFIRMED");
            confirmedNow = true;
        } else if ("CONFIRMED".equals(request.getStatus())) {
            // If status is provided and is CONFIRMED → perform full confirmation flow
            if (!"CREATED".equals(order.getOrderStatus())) {
                throw new IllegalStateException("Order must be in CREATED state to confirm");
            }
            if (!"PAID".equals(order.getPaymentStatus()) && !"PARTIALLY_PAID".equals(order.getPaymentStatus())) {
                throw new IllegalStateException("Payment must be PAID or PARTIALLY_PAID to confirm order");
            }
            orderService.processOrderConfirmation(orderId);
            confirmedNow = true;
        }

        // Update fields only if provided
        if (request.getStatus() != null) {
            order.setOrderStatus(request.getStatus());
        }
        if (request.getNote() != null) {
            order.setNote(request.getNote());
        }
        order.setUpdatedBy(updatedBy);
        order.setUpdatedAt(LocalDateTime.now());

        // ✅ NEW: Update payment status from request if provided
        if (request.getPaymentStatus() != null) {
            Payment payment = paymentService.findByOrderId(orderId);
            if (payment != null) {
                payment.setStatus(request.getPaymentStatus());
                paymentService.save(payment);
            }
            // Also update order's payment_status field for consistency
            order.setPaymentStatus(request.getPaymentStatus());
        }

        // Send email only on CONFIRMED
        if (confirmedNow) {
            emailService.sendEmail(
                    order.getCustomerEmail(),
                    "Your Order #" + order.getOrderId() + " is Confirmed!",
                    "Thank you! Your payment has been verified. We’ll process your order shortly."
            );
        }

        Order updatedOrder = orderService.save(order);
        return ResponseEntity.ok(updatedOrder);
    }

    // Helper: Get current user's email/username
    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "system";
        }
        if (auth.getPrincipal() instanceof UserDetails) {
            return ((UserDetails) auth.getPrincipal()).getUsername(); // assuming email is stored as username
        }
        return auth.getName();
    }

    // Helper: Map order status → payment status
    private String mapOrderStatusToPaymentStatus(String orderStatus) {
        switch (orderStatus) {
            case "CONFIRMED":
                return "confirmed";
            case "PARTIALLY_PAID":
                return "partially_paid";
            case "REJECTED":
            case "FAILED":
                return "failed";
            case "PENDING_INFO":
                return "pending_info";
            default:
                return "pending"; // fallback
        }
    }

    @PostMapping("/{orderId}/confirm")
    @PreAuthorize("hasAuthority('orders.fulfil')")
    public ResponseEntity<Order> confirmOrder(@PathVariable Long orderId) {
        String updatedBy = getCurrentUserEmail();
        Order order = orderService.findById(orderId);

        // Validate preconditions
        if (!"CREATED".equals(order.getOrderStatus())) {
            throw new IllegalStateException("Order must be in CREATED state to confirm");
        }
        if (!"PAID".equals(order.getPaymentStatus()) && !"PARTIALLY_PAID".equals(order.getPaymentStatus())) {
            throw new IllegalStateException("Payment must be PAID or PARTIALLY_PAID to confirm order");
        }

        // Perform confirmation (stock deduction, etc.)
        orderService.processOrderConfirmation(orderId);

        // Update order status to CONFIRMED
        order.setOrderStatus("CONFIRMED");
        order.setUpdatedBy(updatedBy);
        order.setUpdatedAt(LocalDateTime.now());

        // Save and send email
        Order updatedOrder = orderService.save(order);

        emailService.sendEmail(
                order.getCustomerEmail(),
                "Your Order #" + order.getOrderId() + " is Confirmed!",
                "Thank you! Your payment has been verified. We’ll process your order shortly."
        );

        return ResponseEntity.ok(updatedOrder);
    }

    @PostMapping("/{orderId}/ship")
    @PreAuthorize("hasAuthority('orders.fulfil')")
    public ResponseEntity<Void> shipOrder(@PathVariable Long orderId) {
        String currentUser = getCurrentUserEmail();
        orderService.shipOrder(orderId, currentUser); // ✅ 2 args
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{orderId}/complete")
    @PreAuthorize("hasAuthority('orders.fulfil')")
    public ResponseEntity<Void> completeOrder(@PathVariable Long orderId) {
        orderService.completeOrder(orderId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAuthority('payments.verify')")
    public List<OrderVerificationDTO> getOrdersForVerification(@PathVariable String status) {
        return orderService.getOrdersForVerification(status);
    }
}