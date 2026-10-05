package com.api.inventory.controller;

import com.api.inventory.service.BankPaymentService;
import com.api.inventory.service.BankPaymentService.BankView;
import com.api.inventory.service.payments.BankGatewayClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Paying from a bank account (the page /pay/bank). The customer must own the payment.
 *   GET  /banks              the banks to choose from
 *   GET  /{reference}        where the payment stands
 *   POST /{reference}/code   {bankCode, accountNumber}: the bank sends a code to the account holder's phone
 *   POST /{reference}/pay    {code}: approve the debit
 * Cancelling uses the general POST /api/online-payments/{reference}/cancel.
 * Staff (payments.verify):
 *   GET  /to-check             payments whose result the bank never sent (to check with the bank)
 *   POST /{reference}/settle   {paid, bankJournal}: what the bank said
 */
@RestController
@RequestMapping("/api/online-payments/bank")
@PreAuthorize("isAuthenticated()")
public class BankPaymentController {

    private final BankPaymentService bank;

    public BankPaymentController(BankPaymentService bank) {
        this.bank = bank;
    }

    /** toString hides the account number: request bodies can be written to the log while developing. */
    public record CodeRequest(String bankCode, String accountNumber) {
        @Override
        public String toString() {
            return "CodeRequest[bankCode=" + bankCode + ", accountNumber=****]";
        }
    }

    /** toString hides the code. */
    public record PayRequest(String code) {
        @Override
        public String toString() {
            return "PayRequest[code=****]";
        }
    }

    public record SettleRequest(boolean paid, String bankJournal) {
    }

    @GetMapping("/banks")
    public List<BankGatewayClient.Bank> banks() {
        return bank.banks();
    }

    @PreAuthorize("hasAuthority('payments.verify')")
    @GetMapping("/to-check")
    public List<BankPaymentService.ToCheck> toCheck() {
        return bank.toCheck();
    }

    @PreAuthorize("hasAuthority('payments.verify')")
    @PostMapping("/{reference}/settle")
    public BankView settle(@PathVariable String reference, @RequestBody SettleRequest request) {
        return bank.settle(reference, request.paid(), request.bankJournal());
    }

    @GetMapping("/{reference}")
    public BankView view(@PathVariable String reference) {
        return bank.view(reference);
    }

    @PostMapping("/{reference}/code")
    public BankView requestCode(@PathVariable String reference, @RequestBody CodeRequest request) {
        return bank.requestCode(reference, request.bankCode(), request.accountNumber());
    }

    @PostMapping("/{reference}/pay")
    public BankView pay(@PathVariable String reference, @RequestBody PayRequest request) {
        return bank.pay(reference, request.code());
    }
}
