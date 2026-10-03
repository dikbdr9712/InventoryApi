package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One delivery of one product: how many came, what one cost us, its batch number and expiry date,
 * and how many are still on the shelf. The same product bought twice at different prices is two batches.
 *
 * Sales take from the batch that expires first (FEFO; batches without a date last, oldest first among equals),
 * so old stock leaves before new stock and every sale knows its real cost.
 *
 *   ACTIVE       on sale (while quantityLeft > 0)
 *   EXPIRED      the expiry date passed: taken off sale automatically (quantityLeft = what expired)
 *   WRITTEN_OFF  everything left was written off (damaged, lost)
 *
 * The sum of quantityLeft of ACTIVE batches always equals inventory_stock.current_quantity (StockService keeps it so).
 */
@Entity
@Table(name = "stock_batches", indexes = {
        @Index(name = "idx_batch_item", columnList = "itemId, status"),
        @Index(name = "idx_batch_expiry", columnList = "expiryDate")
})
@Getter
@Setter
public class StockBatch {

    public static final String ACTIVE = "ACTIVE";
    public static final String EXPIRED = "EXPIRED";
    public static final String WRITTEN_OFF = "WRITTEN_OFF";

    /** Where the stock came from. */
    public static final String PURCHASE = "PURCHASE";
    public static final String OPENING = "OPENING";      // stock that existed before batches were tracked
    public static final String ADJUSTMENT = "ADJUSTMENT"; // a stock count found more than the system had
    public static final String RETURN = "RETURN";         // a customer returned it and it had no batch
    public static final String SELLER = "SELLER";         // a marketplace seller's own stock

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long itemId;

    /** The batch or lot number printed on the pack (optional). */
    @Column(length = 60)
    private String batchNo;

    /** Empty = does not expire (or not known). */
    private LocalDate expiryDate;

    /** What ONE cost us. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer quantityReceived;

    @Column(nullable = false)
    private Integer quantityLeft;

    @Column(nullable = false, length = 12)
    private String status = ACTIVE;

    @Column(nullable = false, length = 12)
    private String source = PURCHASE;

    @Column(length = 100)
    private String supplier;

    @Column(nullable = false)
    private Instant receivedAt;

    @Column(length = 120)
    private String receivedBy;

    @Column(length = 300)
    private String note;

    public boolean isExpiredOn(LocalDate day) {
        return expiryDate != null && expiryDate.isBefore(day);
    }
}
