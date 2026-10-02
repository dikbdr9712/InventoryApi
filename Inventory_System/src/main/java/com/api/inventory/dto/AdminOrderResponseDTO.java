// src/main/java/com/api/inventory/dto/AdminOrderResponseDTO.java
package com.api.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminOrderResponseDTO {
    private Long orderId;
    private String customerName;
    private String customerEmail;
    private String orderStatus;
    private String paymentStatus;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AdminOrderItemDTO> items; // enriched with stock

    // what staff need to decide the next step
    private String customerPhone;
    private String source;             // ONLINE or POS
    private boolean paymentSubmitted;  // the customer sent a payment (journal number) that waits to be checked
    private String paymentMethod;
    private String journalNumber;
    private BigDecimal paymentAmount;
    private boolean hasPackages;       // delivered package by package (Deliveries board), not with Ship / Delivered
	public Long getOrderId() {
		return orderId;
	}
	public void setOrderId(Long orderId) {
		this.orderId = orderId;
	}
	public String getCustomerName() {
		return customerName;
	}
	public void setCustomerName(String customerName) {
		this.customerName = customerName;
	}
	public String getCustomerEmail() {
		return customerEmail;
	}
	public void setCustomerEmail(String customerEmail) {
		this.customerEmail = customerEmail;
	}
	public String getOrderStatus() {
		return orderStatus;
	}
	public void setOrderStatus(String orderStatus) {
		this.orderStatus = orderStatus;
	}
	public BigDecimal getTotalAmount() {
		return totalAmount;
	}
	public void setTotalAmount(BigDecimal totalAmount) {
		this.totalAmount = totalAmount;
	}
	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
	public List<AdminOrderItemDTO> getItems() {
		return items;
	}
	public void setItems(List<AdminOrderItemDTO> items) {
		this.items = items;
	}
	public String getPaymentStatus() {
		return paymentStatus;
	}
	public void setPaymentStatus(String paymentStatus) {
		this.paymentStatus = paymentStatus;
	}
    
    
}