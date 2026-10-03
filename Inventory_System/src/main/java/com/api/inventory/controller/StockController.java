package com.api.inventory.controller;

import com.api.inventory.service.StockService;
import com.api.inventory.service.StockService.BatchView;
import com.api.inventory.service.StockService.ProfitReport;
import com.api.inventory.service.StockService.Summary;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Stock by batch: what is on the shelf, what expires soon, what expired, and profit.
 *   read:    stock.restock, items.manage or reports.view
 *   change:  stock.restock (batch number / expiry, write-offs, stock counts)
 *   profit:  reports.view
 */
@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService stock;

    public StockController(StockService stock) {
        this.stock = stock;
    }

    public record BatchChange(String batchNo, LocalDate expiryDate) {
    }

    public record WriteOff(Integer quantity, String reason) {
    }

    public record Count(Integer quantity, BigDecimal unitCost, String note) {
    }

    @PreAuthorize("hasAnyAuthority('stock.restock', 'items.manage', 'reports.view')")
    @GetMapping("/summary")
    public Summary summary(@RequestParam(defaultValue = "60") int days) {
        return stock.summary(Math.max(1, Math.min(days, 365)));
    }

    @PreAuthorize("hasAnyAuthority('stock.restock', 'items.manage', 'reports.view')")
    @GetMapping("/batches")
    public List<BatchView> batches(@RequestParam(defaultValue = "shelf") String view, @RequestParam(defaultValue = "60") int days) {
        return stock.list(view, Math.max(1, Math.min(days, 365)));
    }

    @PreAuthorize("hasAnyAuthority('stock.restock', 'items.manage', 'reports.view')")
    @GetMapping("/items/{itemId}/batches")
    public List<BatchView> batchesOf(@PathVariable Long itemId) {
        return stock.batchesOf(itemId);
    }

    @PreAuthorize("hasAuthority('stock.restock')")
    @PutMapping("/batches/{id}")
    public BatchView change(@PathVariable Long id, @RequestBody BatchChange request) {
        return stock.updateDetails(id, request.batchNo(), request.expiryDate());
    }

    @PreAuthorize("hasAuthority('stock.restock')")
    @PostMapping("/batches/{id}/write-off")
    public BatchView writeOff(@PathVariable Long id, @RequestBody WriteOff request) {
        return stock.writeOff(id, request.quantity() == null ? 0 : request.quantity(), request.reason());
    }

    /** A stock count: the shelf has exactly this many. */
    @PreAuthorize("hasAuthority('stock.restock')")
    @PostMapping("/items/{itemId}/count")
    public List<BatchView> count(@PathVariable Long itemId, @RequestBody Count request) {
        if (request.quantity() == null) {
            throw new IllegalArgumentException("Enter how many are on the shelf.");
        }
        stock.setCount(itemId, request.quantity(), request.unitCost(), null,
                request.note() == null || request.note().isBlank() ? "Stock count" : "Stock count: " + request.note().trim());
        return stock.batchesOf(itemId);
    }

    @PreAuthorize("hasAuthority('reports.view')")
    @GetMapping("/profit")
    public ProfitReport profit(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("The end date is before the start date.");
        }
        return stock.profit(from, to);
    }
}
