package com.api.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** One line of a return: how many of one item came back, and what was refunded for each. */
@Entity
@Table(name = "sales_return_items", indexes = @Index(name = "idx_sri_order_item", columnList = "order_item_id"))
@Getter
@Setter
@NoArgsConstructor
public class SalesReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "return_item_id")
    private Long returnItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_id", nullable = false)
    private SalesReturn salesReturn;

    /** The line of the sale this came from (order_items.order_item_id) */
    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** Refund for ONE unit: what the customer really paid, after discount, with tax */
    @Column(name = "unit_refund", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitRefund;

    /** true = the items went back on the shelf, false = for example damaged, not put back in stock */
    @Column(name = "restocked", nullable = false)
    private Boolean restocked;

    // Written out by hand because SalesReturn.addItem() calls it (Lombok skips a method that already exists)
    public void setSalesReturn(SalesReturn salesReturn) {
        this.salesReturn = salesReturn;
    }
}