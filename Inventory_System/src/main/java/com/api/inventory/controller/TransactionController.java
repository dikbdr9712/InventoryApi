package com.api.inventory.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.api.inventory.dto.TransactionRequestDTO;
import com.api.inventory.service.TransactionService;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    @Autowired
    private TransactionService transactionService;

    @PostMapping("/sale")
    @PreAuthorize("hasAuthority('pos.use')")
    public void recordSale(@RequestBody TransactionRequestDTO request) {
        transactionService.recordSale(request);
    }

    @PostMapping("/purchase")
    @PreAuthorize("hasAuthority('stock.restock')")
    public void recordPurchase(@RequestBody TransactionRequestDTO request) {
        transactionService.recordPurchase(request);
    }
}