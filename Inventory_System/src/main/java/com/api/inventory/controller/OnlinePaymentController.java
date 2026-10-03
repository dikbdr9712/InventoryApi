package com.api.inventory.controller;

import com.api.inventory.service.OnlinePaymentService;
import com.api.inventory.service.OnlinePaymentService.IntentView;
import com.api.inventory.service.OnlinePaymentService.Option;
import com.api.inventory.service.OnlinePaymentService.StartResult;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Paying online.
 *   customers: the ways to pay, start a payment, see how it went, cancel it (and the test page's buttons)
 *   gateways:  POST /callback/{code} (no sign-in; the gateway's signature is checked instead)
 */
@RestController
@RequestMapping("/api/online-payments")
public class OnlinePaymentController {

    private final OnlinePaymentService payments;

    public OnlinePaymentController(OnlinePaymentService payments) {
        this.payments = payments;
    }

    public record StartRequest(Long orderId, String provider) {
    }

    public record OutcomeRequest(String outcome) {
    }

    @GetMapping("/options")
    public List<Option> options() {
        return payments.options();
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/start")
    public StartResult start(@RequestBody StartRequest request) {
        return payments.start(request.orderId(), request.provider());
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{reference}")
    public IntentView status(@PathVariable String reference) {
        return payments.status(reference);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{reference}/cancel")
    public IntentView cancel(@PathVariable String reference) {
        payments.status(reference); // only the owner
        payments.cancel(reference);
        return payments.status(reference);
    }

    /** The test gateway's page: Pay / Fail / Cancel. Refused unless the test gateway is switched on. */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/sandbox/{reference}")
    public IntentView sandbox(@PathVariable String reference, @RequestBody OutcomeRequest request) {
        return payments.completeSandbox(reference, request.outcome());
    }

    /** Server-to-server message from a payment gateway (form fields or JSON, depending on the gateway). */
    @PostMapping("/callback/{provider}")
    public ResponseEntity<Map<String, String>> callback(@PathVariable String provider, @RequestParam Map<String, String> form,
                                                        @RequestBody(required = false) Map<String, String> json) {
        Map<String, String> params = json != null && !json.isEmpty() ? json : form;
        payments.callback(provider, params);
        return ResponseEntity.ok(Map.of("status", "received"));
    }
}
