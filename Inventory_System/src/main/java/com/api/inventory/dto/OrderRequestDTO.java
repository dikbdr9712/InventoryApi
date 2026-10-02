package com.api.inventory.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequestDTO {
    private String customerName;
    private String customerEmail;
    private String customerPhone;   // ✅ Add this
    private String address;
    // Where to deliver on the map: the phone's location, or a delivery area (the area wins). Both optional.
    private Double dropLatitude;
    private Double dropLongitude;
    private Long areaId;
    private BigDecimal totalAmount;
    
    // Primary format: list of items (used by cart)
    private List<Item> items;
    
    // Fallback fields for single-item "Buy Now" flow
    private Long itemId;
    private Integer quantity;

    // Getters and setters (Lombok usually handles these, but kept for clarity if not using Lombok fully)
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

	public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = (items != null) ? items : new ArrayList<>();
    }

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

    /**
     * Returns a normalized list of items.
     * - If 'items' is provided and non-empty → use it.
     * - Else, if 'itemId' and 'quantity' are provided → create a single-item list.
     * - Else → throw an exception.
     */
    public List<Item> getNormalizedItems() {
        if (items != null && !items.isEmpty()) {
            return items;
        }

        if (itemId != null && quantity != null && quantity > 0) {
            List<Item> singleItemList = new ArrayList<>();
            Item singleItem = new Item();
            singleItem.setItemId(itemId);
            singleItem.setQuantity(quantity);
            // Note: price will be fetched from DB in service — ignore client-provided price
            singleItemList.add(singleItem);
            return singleItemList;
        }

        throw new IllegalArgumentException("Order must contain either 'items' array or 'itemId' and 'quantity'.");
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long itemId;
        private Integer quantity;
        private BigDecimal price; // Optional: can be ignored during order creation

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

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }
    }
}