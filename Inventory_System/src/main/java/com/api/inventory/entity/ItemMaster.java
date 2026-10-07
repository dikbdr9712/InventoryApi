package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "item_master")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemMaster {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long itemId;
    @Column(name = "sku", unique = true, nullable = false)
    private String sku;
    @Column(name = "item_name", nullable = false)
    private String itemName;
    private String barcode;
    private String supplierItemCode;
    private String description;
    private String uom;
    @Column(name = "cost_price", precision = 10, scale = 2)
    private BigDecimal costPrice = BigDecimal.ZERO;
    @Column(name = "markup_percent", precision = 5, scale = 2)
    private BigDecimal markupPercent; 
    @Column(name = "selling_price", precision = 10, scale = 2)
    private BigDecimal sellingPrice = BigDecimal.ZERO;
    @Column(name = "mrp", precision = 10, scale = 2)
    private BigDecimal mrp = BigDecimal.ZERO;
    @Transient // ← Important: Not persisted
    public BigDecimal getMarkupPercentage() {
        if (costPrice == null || costPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO; // or null, depending on your UI
        }
        BigDecimal markup = sellingPrice.subtract(costPrice)
                                       .divide(costPrice, 4, RoundingMode.HALF_UP)
                                       .multiply(BigDecimal.valueOf(100));
        return markup.setScale(2, RoundingMode.HALF_UP);
    }

    @Column(name = "discount_allowed")
    private Boolean discountAllowed = true;

    @Column(name = "max_discount_percent", precision = 5, scale = 2)
    private BigDecimal maxDiscountPercent = new BigDecimal("100.00");

    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @PrePersist
    public void generateSkuAndTimestamp() {
        if (this.sku == null || this.sku.trim().isEmpty()) {
            this.sku = "ITEM-" + Instant.now().toEpochMilli();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
    
    private LocalDateTime createdAt;
    @Column(name = "category", length = 100)
    private String category;
    @Column(name = "image_path")
    private String imagePath;

    /** "Warn me when stock reaches": stock at or below this number tells the people who restock. Empty = no warning. */
    @Column(name = "low_stock_threshold")
    private Integer lowStockThreshold;

    /** The SellerProfile that sells this product. Empty = our own shop's product. */
    @Column(name = "seller_id")
    private Long sellerId;

    /** How big it is to deliver: SMALL, MEDIUM, LARGE or BULKY (see DeliverySize). Empty = small. */
    @Column(name = "delivery_size", length = 10)
    private String deliverySize;
	public Long getItemId() {
		return itemId;
	}

	public void setItemId(Long itemId) {
		this.itemId = itemId;
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

	public BigDecimal getCostPrice() {
		return costPrice;
	}

	public void setCostPrice(BigDecimal costPrice) {
		this.costPrice = costPrice;
	}
	

	public BigDecimal getMarkupPercent() {
		return markupPercent;
	}

	public void setMarkupPercent(BigDecimal markupPercent) {
		this.markupPercent = markupPercent;
	}

	public BigDecimal getSellingPrice() {
		return sellingPrice;
	}

	public void setSellingPrice(BigDecimal sellingPrice) {
		this.sellingPrice = sellingPrice;
	}
	

	public BigDecimal getMrp() {
		return mrp;
	}

	public void setMrp(BigDecimal mrp) {
		this.mrp = mrp;
	}

	public Boolean getDiscountAllowed() {
		return discountAllowed;
	}

	public void setDiscountAllowed(Boolean discountAllowed) {
		this.discountAllowed = discountAllowed;
	}

	public BigDecimal getMaxDiscountPercent() {
		return maxDiscountPercent;
	}

	public void setMaxDiscountPercent(BigDecimal maxDiscountPercent) {
		this.maxDiscountPercent = maxDiscountPercent;
	}

	public BigDecimal getTaxRate() {
		return taxRate;
	}

	public void setTaxRate(BigDecimal taxRate) {
		this.taxRate = taxRate;
	}

	public Boolean getIsActive() {
		return isActive;
	}

	public void setIsActive(Boolean isActive) {
		this.isActive = isActive;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getImagePath() {
		return imagePath;
	}

	public void setImagePath(String imagePath) {
		this.imagePath = imagePath;
	}
	
}
	