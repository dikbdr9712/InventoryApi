package com.api.inventory.dto;

import java.math.BigDecimal;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequestDTO {
    private Long itemId;
    private String sku; 
    private Integer quantity;
    private BigDecimal unitPrice; // Purchase cost
    private String customerOrSupplier; 
    private String notes;    
             // Required
    private String itemName;          // Required if new item
    private String description;       // Optional
    private String uom;               // Optional (default: "pcs")
    private BigDecimal pricePerUnit;  // Optional — older name for a new product's selling price
    /** A new product's selling price (the restock page). */
    private BigDecimal sellingPrice;
    private String barcode;           // Optional
    private String supplierItemCode;  // Optional — supplier's SKU
    private String supplier;

    // The delivery's batch (pharmacy packs carry both). Optional: empty = not tracked for this product.
    private String batchNo;
    private java.time.LocalDate expiryDate;

    /** Optional: change the product's selling price at the same time (for example the new stock cost more). */
    private BigDecimal newSellingPrice;
    
    
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
	public BigDecimal getUnitPrice() {
		return unitPrice;
	}
	public void setUnitPrice(BigDecimal unitPrice) {
		this.unitPrice = unitPrice;
	}
	public String getCustomerOrSupplier() {
		return customerOrSupplier;
	}
	public void setCustomerOrSupplier(String customerOrSupplier) {
		this.customerOrSupplier = customerOrSupplier;
	}
	public String getNotes() {
		return notes;
	}
	public void setNotes(String notes) {
		this.notes = notes;
	}
	public String getSku() {
		return sku;
	}
	public void setSku(String sku) {
		this.sku = sku;
	}
	public String getItemName() {
		return itemName;
	}
	public void setItemName(String itemName) {
		this.itemName = itemName;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getUom() {
		return uom;
	}
	public void setUom(String uom) {
		this.uom = uom;
	}
	public BigDecimal getPricePerUnit() {
		return pricePerUnit;
	}
	public void setPricePerUnit(BigDecimal pricePerUnit) {
		this.pricePerUnit = pricePerUnit;
	}
	public String getBarcode() {
		return barcode;
	}
	public void setBarcode(String barcode) {
		this.barcode = barcode;
	}
	public String getSupplierItemCode() {
		return supplierItemCode;
	}
	public void setSupplierItemCode(String supplierItemCode) {
		this.supplierItemCode = supplierItemCode;
	}
	public String getSupplier() {
		return supplier;
	}
	public void setSupplier(String supplier) {
		this.supplier = supplier;
	}
	
	
}