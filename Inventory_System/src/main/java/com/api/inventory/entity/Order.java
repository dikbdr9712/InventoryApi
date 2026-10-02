package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;
    @Column(name = "customer_name", nullable = true)
    private String customerName;
    private String customerEmail;
    @Column(name = "customer_phone", nullable = true)
    private String customerPhone;
    private String address;
    /** The delivery point on the map (from the customer's phone, or the delivery area they chose). Empty = not given. */
    private Double dropLatitude;
    private Double dropLongitude;
    /** How the point was found: "Your location (GPS)" or the area's name. */
    @Column(length = 120)
    private String dropLocation;
    private String orderStatus;
    @Column(name = "tax_amount", precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;
    
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    // ✅ Auto-set on insert
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ✅ Auto-set on every update
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "payment_status")
    private String paymentStatus = "PENDING"; // PENDING, PAID, FAILED

    @Column(name = "shipment_id")
    private String shipmentId; 
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", referencedColumnName = "orderId")
    private Payment payment;
    
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
    
    @Column(name = "updated_by")
    private String updatedBy;
    @Column(name = "payment_method")
    private String paymentMethod; 
    @Column(name = "source", nullable = false)
    private String source = "ONLINE"; // default

    /** Delivery fees included in totalAmount (one fee per package). Empty on older orders. */
    @Column(name = "delivery_fee", precision = 12, scale = 2)
    private BigDecimal deliveryFee;

    /** The customer this order belongs to (online buyer, or a counter customer who gave a phone number). */
    @Column(name = "customer_id")
    private Long customerId;

    // ----- counter sales -----

    /** The cash drawer shift this counter sale belongs to. */
    @Column(name = "shift_id")
    private Long shiftId;

    /** Email of the cashier who made the counter sale. */
    @Column(name = "cashier", length = 150)
    private String cashier;

    /** The till's own id for the sale, so pressing Complete twice (or a network retry) never saves it twice. */
    @Column(name = "client_ref", length = 64, unique = true)
    private String clientRef;

    /** Cash handed over by the customer (cash sales), for the receipt. */
    @Column(name = "amount_tendered", precision = 12, scale = 2)
    private BigDecimal amountTendered;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<TaxDetail> taxDetails;


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
	

	public String getCustomerPhone() {
		return customerPhone;
	}

	public void setCustomerPhone(String customerPhone) {
		this.customerPhone = customerPhone;
	}


	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getOrderStatus() {
		return orderStatus;
	}

	public void setOrderStatus(String orderStatus) {
		this.orderStatus = orderStatus;
	}
	
	

	public BigDecimal getTaxAmount() {
		return taxAmount;
	}

	public void setTaxAmount(BigDecimal taxAmount) {
		this.taxAmount = taxAmount;
	}

	public BigDecimal getDiscountAmount() {
		return discountAmount;
	}

	public void setDiscountAmount(BigDecimal discountAmount) {
		this.discountAmount = discountAmount;
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

	public String getPaymentStatus() {
		return paymentStatus;
	}

	public void setPaymentStatus(String paymentStatus) {
		this.paymentStatus = paymentStatus;
	}

	public String getShipmentId() {
		return shipmentId;
	}

	public void setShipmentId(String shipmentId) {
		this.shipmentId = shipmentId;
	}
	
	

	public Payment getPayment() {
		return payment;
	}

	public void setPayment(Payment payment) {
		this.payment = payment;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(String updatedBy) {
		this.updatedBy = updatedBy;
	}

	public String getPaymentMethod() {
		return paymentMethod;
	}

	public void setPaymentMethod(String paymentMethod) {
		this.paymentMethod = paymentMethod;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public List<TaxDetail> getTaxDetails() {
		return taxDetails;
	}

	public void setTaxDetails(List<TaxDetail> taxDetails) {
		this.taxDetails = taxDetails;
	}

	
    
    
}