package com.api.inventory.controller;

import com.api.inventory.dto.SalesReportDTO;
import com.api.inventory.dto.SalesSummaryDTO;
import com.api.inventory.service.SalesReportService;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('reports.view')") // was open to anyone
public class SalesReportController {

    @Autowired
    private SalesReportService salesReportService;

    @GetMapping("/sales")
    public ResponseEntity<List<SalesReportDTO>> getSalesReport(
            @RequestParam(defaultValue = "today") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        List<SalesReportDTO> report = salesReportService.getSalesReportByPeriod(period, startDate, endDate);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/sales/summary")
    public ResponseEntity<SalesSummaryDTO> getSalesSummary(
            @RequestParam(defaultValue = "today") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        SalesSummaryDTO summary = salesReportService.getSalesSummaryByPeriod(period, startDate, endDate);
        return ResponseEntity.ok(summary);
    }
}