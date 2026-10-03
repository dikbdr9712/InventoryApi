package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Which batch a sold line came from, and how many (one line can take from two batches when the first runs out).
 * Gives the exact cost of every sale, puts a return or a cancelled order back into the same batch,
 * and answers "who bought batch X?" if a medicine is ever recalled.
 */
@Entity
@Table(name = "order_item_batches", indexes = {
        @Index(name = "idx_oib_line", columnList = "orderItemId"),
        @Index(name = "idx_oib_batch", columnList = "batchId")
})
@Getter
@Setter
public class OrderItemBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderItemId;

    @Column(nullable = false)
    private Long batchId;

    @Column(nullable = false)
    private Integer quantity;

    /** How many of these came back (returned or the order was cancelled). */
    @Column(nullable = false)
    private Integer returnedQuantity = 0;

    /** The batch's cost of one at the time of the sale. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;
}
