package com.api.inventory.controller;

import com.api.inventory.service.CustomerService;
import com.api.inventory.service.CustomerService.CustomerDetail;
import com.api.inventory.service.CustomerService.CustomerSummary;
import com.api.inventory.service.CustomerService.CustomerUpdate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Customers for staff: the list (search by name, phone or email), one customer's details and order history,
 * corrections, and the till's "have we seen this phone number before?".
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('customers.view')")
    public List<CustomerSummary> list(@RequestParam(required = false) String q, @RequestParam(defaultValue = "100") int limit) {
        return customers.list(q, limit);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('customers.view')")
    public CustomerDetail detail(@PathVariable Long id) {
        return customers.detail(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('customers.manage')")
    public CustomerDetail update(@PathVariable Long id, @RequestBody CustomerUpdate update) {
        return customers.update(id, update);
    }

    /** The answer the till expects (unchanged since before the customer list existed). */
    public record CustomerLookup(boolean found, String name, Long visits, BigDecimal totalSpent, Instant lastVisit) {
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAnyAuthority('pos.use','customers.view')")
    public CustomerLookup lookup(@RequestParam String phone) {
        String p = phone == null ? "" : phone.trim();
        if (!p.matches("^[0-9]{8}$")) {
            throw new IllegalArgumentException("Enter an 8-digit phone number.");
        }
        return customers.byPhone(p)
                .map(c -> new CustomerLookup(true, c.name(), c.visits(), c.totalSpent(), c.lastSeenAt()))
                .orElse(new CustomerLookup(false, null, 0L, BigDecimal.ZERO, null));
    }
}
