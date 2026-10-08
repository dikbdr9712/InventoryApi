package com.api.inventory.controller;

import com.api.inventory.security.CurrentUser;
import com.api.inventory.service.StockAlertService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** "Notify me when it is back" on sold-out products, for the signed-in person. */
@RestController
@RequestMapping("/api/stock-alerts")
public class StockAlertController {

    private final StockAlertService alerts;

    public StockAlertController(StockAlertService alerts) {
        this.alerts = alerts;
    }

    /** The products this person is waiting for. */
    @GetMapping
    public List<Long> mine() {
        return alerts.waiting(CurrentUser.email());
    }

    @PostMapping("/{itemId}")
    public void ask(@PathVariable Long itemId) {
        alerts.ask(CurrentUser.email(), itemId);
    }

    @DeleteMapping("/{itemId}")
    public void cancel(@PathVariable Long itemId) {
        alerts.cancel(CurrentUser.email(), itemId);
    }
}
