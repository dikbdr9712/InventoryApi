package com.api.inventory.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One return: a customer brings back items from an earlier sale.
 * The original order is never changed. A sale counts as "returned" because a row like this exists for it.
 */
@Entity
@Table(name = "sales_returns", indexes = @Index(name = "idx_sales_returns_order", columnList = "order_id"))
@Getter
@Setter
@NoArgsConstructor
public class SalesReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "return_id")
    private Long returnId;

    /** The sale the items came from (orders.order_id) */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Who handled the return (the signed-in staff member) */
    @Column(name = "created_by")
    private String createdBy;

    /** For example DAMAGED, WRONG_ITEM, CHANGED_MIND, OTHER */
    @Column(name = "reason", nullable = false, length = 100)
    private String reason;

    @Column(name = "note")
    private String note;

    /** For example CASH or ORIGINAL (back to the way the customer paid) */
    @Column(name = "refund_method", nullable = false, length = 30)
    private String refundMethod;

    /** Money given back for the whole return, tax included */
    @Column(name = "refund_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @OneToMany(mappedBy = "salesReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SalesReturnItem> items = new ArrayList<>();

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** Adds a line and links it back to this return */
    public void addItem(SalesReturnItem item) {
        item.setSalesReturn(this);
        items.add(item);
    }
}