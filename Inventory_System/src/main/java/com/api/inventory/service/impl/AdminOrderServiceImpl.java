// src/main/java/com/api/inventory/service/impl/AdminOrderServiceImpl.java
package com.api.inventory.service.impl;

import com.api.inventory.dto.AdminOrderItemDTO;
import com.api.inventory.dto.AdminOrderResponseDTO;
import com.api.inventory.entity.*;
import com.api.inventory.repository.*;
import com.api.inventory.service.AdminOrderService;
import com.api.inventory.service.OrderService;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminOrderServiceImpl implements AdminOrderService {
	
	@Autowired
    private OrderRepository orderRepository;
	@Autowired
    private OrderItemRepository orderItemRepository;
	@Autowired
    private ItemMasterRepository itemMasterRepository;
	@Autowired
    private InventoryStockRepository inventoryStockRepository;
	@Autowired
    private OrderService orderService; // ← delegate to real business logic
	@Autowired
    private com.api.inventory.repository.PaymentRepository paymentRepository;
	@Autowired
    private com.api.inventory.service.PackageService packageService;

    @Override
    public List<AdminOrderResponseDTO> getAllOrdersForAdmin() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToAdminOrderResponse)
                .collect(Collectors.toList());
    }
    
    private AdminOrderResponseDTO mapToAdminOrderResponse(Order order) {
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getOrderId());
        List<Long> itemIds = orderItems.stream()
                .map(OrderItem::getItemId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, ItemMaster> itemMap = new HashMap<>();
        Map<Long, InventoryStock> stockMap = new HashMap<>();

        if (!itemIds.isEmpty()) {
            itemMasterRepository.findAllById(itemIds)
                    .forEach(item -> itemMap.put(item.getItemId(), item));
            inventoryStockRepository.findByItemIdIn(itemIds)
                    .forEach(stock -> stockMap.put(stock.getItemId(), stock));
        }

        List<AdminOrderItemDTO> enrichedItems = orderItems.stream()
                .map(oi -> {
                    AdminOrderItemDTO dto = new AdminOrderItemDTO();
                    dto.setOrderItemId(oi.getOrderItemId());
                    dto.setItemId(oi.getItemId());
                    dto.setQuantityOrdered(oi.getQuantity());
                    dto.setUnitPrice(oi.getUnitPrice());

                    ItemMaster item = itemMap.get(oi.getItemId());
                    if (item != null) {
                        dto.setItemName(item.getItemName());
                        dto.setUom(item.getUom());
                        dto.setImagePath(item.getImagePath());
                    } else {
                        dto.setItemName("Item Deleted");
                    }

                    InventoryStock stock = stockMap.get(oi.getItemId());
                    dto.setStockAvailable(stock != null ? stock.getCurrentQuantity() : 0);
                    return dto;
                })
                .collect(Collectors.toList());

        // ✅ Create response object FIRST
        AdminOrderResponseDTO response = new AdminOrderResponseDTO();
        
        // ✅ Then set fields (with null safety if needed)
        response.setOrderId(order.getOrderId());
        response.setCustomerName(order.getCustomerName() != null ? order.getCustomerName() : "Unknown");
        response.setCustomerEmail(order.getCustomerEmail() != null ? order.getCustomerEmail() : "No email");
        response.setOrderStatus(order.getOrderStatus());
        response.setPaymentStatus(order.getPaymentStatus());
        response.setTotalAmount(order.getTotalAmount());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());
        response.setItems(enrichedItems);
        response.setCustomerPhone(order.getCustomerPhone());
        response.setSource(order.getSource());
        response.setHasPackages(packageService.hasPackages(order.getOrderId()));
        paymentRepository.findByOrderId(order.getOrderId()).ifPresent(p -> {
            response.setPaymentMethod(p.getPaymentMethod());
            response.setJournalNumber(p.getJournalNumber());
            response.setPaymentAmount(p.getAmount());
            response.setPaymentSubmitted("pending".equalsIgnoreCase(p.getStatus()) || "partially_paid".equalsIgnoreCase(p.getStatus()));
        });
        
        return response;
    }

    // === DELEGATE TO REAL BUSINESS LOGIC ===

    @Override
    public void confirmOrder(Long orderId) {
        orderService.confirmOrder(orderId); // ← your existing logic with stock deduction
    }

    @Override
    public void cancelOrder(Long orderId) {
        orderService.cancelOrder(orderId);
    }

    @Override
    public void completeOrder(Long orderId) {
        orderService.completeOrder(orderId);
    }

    // Optional: for UI preview (non-transactional)
    @Override
    public boolean canOrderBeConfirmed(Long orderId) {
        return orderItemRepository.findByOrderId(orderId).stream().allMatch(item -> {
            Optional<InventoryStock> stockOpt = inventoryStockRepository.findByItemId(item.getItemId());
            int available = stockOpt.map(InventoryStock::getCurrentQuantity).orElse(0);
            return available >= item.getQuantity();
        });
    }
}