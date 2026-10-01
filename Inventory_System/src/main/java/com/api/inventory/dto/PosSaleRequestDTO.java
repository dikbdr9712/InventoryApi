package com.api.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PosSaleRequestDTO {
    private String customerName;
    private String customerPhone;
    private String paymentMethod; // "CASH", "CARD", "UPI", etc.
    private BigDecimal mrp;              // ← ADD THIS
    private BigDecimal discountPercent; 
    private java.math.BigDecimal discountTotal; // total discount amount (not %)
    private List<TaxInfoDTO> taxes;               // list of applied taxes
    private List<ItemQty> items;
	public String getCustomerName() {
		return customerName;
	}
	public void setCustomerName(String customerName) {
		this.customerName = customerName;
	}
	public String getCustomerPhone() {
		return customerPhone;
	}
	public void setCustomerPhone(String customerPhone) {
		this.customerPhone = customerPhone;
	}
	public String getPaymentMethod() {
		return paymentMethod;
	}
	public void setPaymentMethod(String paymentMethod) {
		this.paymentMethod = paymentMethod;
	}
	public List<ItemQty> getItems() {
		return items;
	}
	public void setItems(List<ItemQty> items) {
		this.items = items;
	}
	
	public BigDecimal getMrp() {
		return mrp;
	}
	public void setMrp(BigDecimal mrp) {
		this.mrp = mrp;
	}
	public BigDecimal getDiscountPercent() {
		return discountPercent;
	}
	public void setDiscountPercent(BigDecimal discountPercent) {
		this.discountPercent = discountPercent;
	}
	public java.math.BigDecimal getDiscountTotal() {
		return discountTotal;
	}
	public void setDiscountTotal(java.math.BigDecimal discountTotal) {
		this.discountTotal = discountTotal;
	}
	public List<TaxInfoDTO> getTaxes() {
		return taxes;
	}
	public void setTaxes(List<TaxInfoDTO> taxes) {
		this.taxes = taxes;
	}
    
	@Data
	public static class ItemQty {
	    private Long itemId;
	    private Integer quantity;
	    private BigDecimal mrp;
	    private java.math.BigDecimal discountPercent; // or BigDecimal
	    private java.math.BigDecimal unitPrice;
		public Long getItemId() {
			return itemId;
		}
		public void setItemId(Long itemId) {
			this.itemId = itemId;
		}
		public Integer getQuantity() {
			return quantity;
		}
		public void setQuantity(Integer quantity) {
			this.quantity = quantity;
		}
		
		public BigDecimal getMrp() {
			return mrp;
		}
		public void setMrp(BigDecimal mrp) {
			this.mrp = mrp;
		}
		public java.math.BigDecimal getDiscountPercent() {
			return discountPercent;
		}
		public void setDiscountPercent(java.math.BigDecimal discountPercent) {
			this.discountPercent = discountPercent;
		}
		public java.math.BigDecimal getUnitPrice() {
			return unitPrice;
		}
		public void setUnitPrice(java.math.BigDecimal unitPrice) {
			this.unitPrice = unitPrice;
		} 
	    
	    
	}
}
