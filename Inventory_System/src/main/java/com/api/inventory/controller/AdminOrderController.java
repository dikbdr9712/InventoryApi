// AdminOrderController.java
package com.api.inventory.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.api.inventory.dto.AdminOrderResponseDTO;
import com.api.inventory.dto.VerificationRequestDTO;
import com.api.inventory.service.AdminOrderService;
import com.api.inventory.service.OrderService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {
    @Autowired
    private AdminOrderService adminOrderService;
    @Autowired
    private OrderService orderService;

    @GetMapping
    @PreAuthorize("hasAuthority('orders.view')")
    public ResponseEntity<List<AdminOrderResponseDTO>> getAllOrders() {
        return ResponseEntity.ok(adminOrderService.getAllOrdersForAdmin());
    }

    @PostMapping("/{orderId}/confirm")
    @PreAuthorize("hasAuthority('orders.fulfil')")
    public ResponseEntity<Void> confirmOrder(@PathVariable Long orderId) {
        String updatedBy = getCurrentUserEmail();

        VerificationRequestDTO dto = new VerificationRequestDTO();
        dto.setStatus("CONFIRMED");
        dto.setNote("Confirmed via admin quick-action");

        // ✅ Now works!
        orderService.confirmOrderWithUser(orderId, "CONFIRMED", dto.getNote(), updatedBy);

        return ResponseEntity.ok().build();
    }

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "system";
        }
        if (auth.getPrincipal() instanceof UserDetails) {
            return ((UserDetails) auth.getPrincipal()).getUsername(); // assumes email is username
        }
        return auth.getName();
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasAuthority('orders.fulfil')")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long orderId) {
        adminOrderService.cancelOrder(orderId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{orderId}/complete")
    @PreAuthorize("hasAuthority('orders.fulfil')")
    public ResponseEntity<Void> completeOrder(@PathVariable Long orderId) {
        adminOrderService.completeOrder(orderId);
        return ResponseEntity.ok().build();
    }
}