package com.api.inventory.controller;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;

// ✅ Dependency Injection
import org.springframework.beans.factory.annotation.Autowired;

// ✅ Java utilities
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ✅ Your entities & services
import com.api.inventory.entity.Order;
import com.api.inventory.service.OrderService;

@RestController
@RequestMapping("/api/admin/reports")
public class ReportController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/sales-by-channel")
    @PreAuthorize("hasAuthority('reports.view')")
    public Map<String, Object> getSalesByChannel() {
        List<Order> posSales = orderService.getPosSales();
        List<Order> onlineOrders = orderService.getOnlineOrders();

        BigDecimal posTotal = posSales.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal onlineTotal = onlineOrders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new HashMap<>();
        result.put("posSalesCount", posSales.size());
        result.put("posTotal", posTotal);
        result.put("onlineSalesCount", onlineOrders.size());
        result.put("onlineTotal", onlineTotal);
        return result;
    }
}