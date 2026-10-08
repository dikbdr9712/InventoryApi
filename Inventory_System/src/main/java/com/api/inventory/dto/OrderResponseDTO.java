package com.api.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.api.inventory.entity.Order;

public class OrderResponseDTO {
	private Long orderId;
    private String customerEmail;
    private String orderStatus;
    private String paymentStatus;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
    
    
	public Long getOrderId() {
		return orderId;
	}
	public void setOrderId(Long orderId) {
		this.orderId = orderId;
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
	public String getPaymentStatus() {
		return paymentStatus;
	}
	public void setPaymentStatus(String paymentStatus) {
		this.paymentStatus = paymentStatus;
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
    
    
	
	// Delivery details and the shop's message, for the customer's order page (only the owner or staff can read an order)
	private String customerName;
	private String customerPhone;
	private String address;
	private String note;
	private String paymentMethod;
	private BigDecimal deliveryFee;
	private String fulfilment; // DELIVERY or PICKUP
	private String couponCode;          // the coupon used (empty = none)
	private BigDecimal couponDiscount;  // what it took off the items

	public String getCustomerName() { return customerName; }
	public String getFulfilment() { return fulfilment; }
	public String getCouponCode() { return couponCode; }
	public BigDecimal getCouponDiscount() { return couponDiscount; }
	public String getCustomerPhone() { return customerPhone; }
	public String getAddress() { return address; }
	public String getNote() { return note; }
	public String getPaymentMethod() { return paymentMethod; }
	public BigDecimal getDeliveryFee() { return deliveryFee; }

	public static OrderResponseDTO fromEntity(Order order) {
	    if (order == null) {
	        return null;
	    }
	    OrderResponseDTO dto = new OrderResponseDTO();
	    dto.setOrderId(order.getOrderId());
	    dto.setCustomerEmail(order.getCustomerEmail());
	    dto.setOrderStatus(order.getOrderStatus());
	    dto.setPaymentStatus(order.getPaymentStatus());
	    dto.setTotalAmount(order.getTotalAmount());
	    dto.setCreatedAt(order.getCreatedAt());
	    dto.customerName = order.getCustomerName();
	    dto.customerPhone = order.getCustomerPhone();
	    dto.address = order.getAddress();
	    dto.note = order.getNote();
	    dto.paymentMethod = order.getPaymentMethod();
	    dto.deliveryFee = order.getDeliveryFee();
	    dto.fulfilment = order.getFulfilment();
	    dto.couponCode = order.getCouponCode();
	    dto.couponDiscount = order.getCouponDiscount();
	    return dto;
	}

}
