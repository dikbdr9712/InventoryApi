package com.api.inventory.controller;

import com.api.inventory.security.CurrentUser;
import com.api.inventory.service.ReturnRequestService;
import com.api.inventory.service.ReturnRequestService.Ask;
import com.api.inventory.service.ReturnRequestService.CustomerView;
import com.api.inventory.service.ReturnRequestService.RequestView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Return requests. Customers: GET/POST /api/orders/{orderId}/return-request (their own orders only).
 * Staff who take returns: the list, approve, decline (/api/return-requests).
 */
@RestController
public class ReturnRequestController {

    private final ReturnRequestService requests;

    public ReturnRequestController(ReturnRequestService requests) {
        this.requests = requests;
    }

    public record Decision(String note) {
    }

    @GetMapping("/api/orders/{orderId}/return-request")
    public CustomerView forMyOrder(@PathVariable Long orderId) {
        return requests.forCustomer(orderId, CurrentUser.email());
    }

    @PostMapping("/api/orders/{orderId}/return-request")
    public RequestView ask(@PathVariable Long orderId, @RequestBody Ask ask) {
        return requests.ask(orderId, CurrentUser.email(), ask);
    }

    /** view: open (default), closed or all */
    @GetMapping("/api/return-requests")
    @PreAuthorize("hasAuthority('sales.return')")
    public List<RequestView> list(@RequestParam(defaultValue = "open") String view) {
        return requests.list(view);
    }

    @PostMapping("/api/return-requests/{id}/approve")
    @PreAuthorize("hasAuthority('sales.return')")
    public RequestView approve(@PathVariable Long id, @RequestBody(required = false) Decision decision) {
        return requests.approve(id, decision == null ? null : decision.note(), CurrentUser.email());
    }

    @PostMapping("/api/return-requests/{id}/decline")
    @PreAuthorize("hasAuthority('sales.return')")
    public RequestView decline(@PathVariable Long id, @RequestBody(required = false) Decision decision) {
        return requests.decline(id, decision == null ? null : decision.note(), CurrentUser.email());
    }
}
