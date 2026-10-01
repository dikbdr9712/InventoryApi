package com.api.inventory.controller;

import com.api.inventory.dto.PosSaleResponseDTO;
import com.api.inventory.entity.Order;
import com.api.inventory.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pos")
public class PosController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('pos.use')")
    public ResponseEntity<List<PosSaleResponseDTO>> getPosSalesHistory() {
        List<Order> orders = orderService.getPosSales();
        List<PosSaleResponseDTO> dtos = orders.stream()
                .map(this::convertToDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    private PosSaleResponseDTO convertToDto(Order order) {
        PosSaleResponseDTO dto = new PosSaleResponseDTO();
        dto.setOrderId(order.getOrderId());
        dto.setCustomerName(order.getCustomerName());
        dto.setCustomerPhone(order.getCustomerPhone());
        dto.setCustomerEmail(order.getCustomerEmail());
        dto.setOrderStatus(order.getOrderStatus());
        dto.setPaymentStatus(order.getPaymentStatus());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setTaxAmount(order.getTaxAmount());
        dto.setDiscountAmount(order.getDiscountAmount());
        dto.setPaymentMethod(order.getPaymentMethod());
        dto.setSource(order.getSource());
        dto.setAddress(order.getAddress());
        dto.setNote(order.getNote());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setUpdatedAt(order.getUpdatedAt());
        dto.setUpdatedBy(order.getUpdatedBy());
        return dto;
    }
}