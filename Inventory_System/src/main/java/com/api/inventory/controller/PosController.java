package com.api.inventory.controller;

import com.api.inventory.dto.PosSaleResponseDTO;
import com.api.inventory.entity.Order;
import com.api.inventory.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pos")
public class PosController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private com.api.inventory.service.PosShiftService shifts;

    // ---------- Cash drawer shifts ----------

    /** The signed-in cashier's open shift (204 = no open shift: the till asks them to open one). */
    @GetMapping("/shifts/current")
    @PreAuthorize("hasAuthority('pos.use')")
    public ResponseEntity<com.api.inventory.service.PosShiftService.ShiftReport> currentShift() {
        return shifts.myCurrent().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/shifts/open")
    @PreAuthorize("hasAuthority('pos.use')")
    public com.api.inventory.service.PosShiftService.ShiftReport openShift(@RequestBody(required = false) com.api.inventory.service.PosShiftService.OpenRequest request) {
        return shifts.open(request);
    }

    @PostMapping("/shifts/{id}/close")
    @PreAuthorize("hasAuthority('pos.use')")
    public com.api.inventory.service.PosShiftService.ShiftReport closeShift(@PathVariable Long id,
                                                                            @RequestBody com.api.inventory.service.PosShiftService.CloseRequest request) {
        return shifts.close(id, request);
    }

    /** My shifts; everyone's with pos.shifts.manage. */
    @GetMapping("/shifts")
    @PreAuthorize("hasAuthority('pos.use')")
    public List<com.api.inventory.service.PosShiftService.ShiftReport> listShifts() {
        return shifts.list();
    }

    @GetMapping("/shifts/{id}")
    @PreAuthorize("hasAuthority('pos.use')")
    public com.api.inventory.service.PosShiftService.ShiftReport shift(@PathVariable Long id) {
        return shifts.get(id);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('pos.use')")
    public ResponseEntity<List<PosSaleResponseDTO>> getPosSalesHistory() {
        List<Order> orders = orderService.getPosSales();
        List<PosSaleResponseDTO> dtos = orders.stream()
                .map(this::convertToDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    private PosSaleResponseDTO convertToDto(Order order) {
        PosSaleResponseDTO dto = new PosSaleResponseDTO();
        dto.setOrderId(order.getOrderId());
        dto.setCashier(order.getCashier());
        dto.setShiftId(order.getShiftId());
        dto.setCustomerName(order.getCustomerName());
        dto.setCustomerPhone(order.getCustomerPhone());
        dto.setCustomerEmail(order.getCustomerEmail());
        dto.setOrderStatus(order.getOrderStatus());
        dto.setPaymentStatus(order.getPaymentStatus());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setTaxAmount(order.getTaxAmount());
        dto.setDiscountAmount(order.getDiscountAmount());
        dto.setPaymentMethod(order.getPaymentMethod());
        dto.setSource(order.getSource());
        dto.setAddress(order.getAddress());
        dto.setNote(order.getNote());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setUpdatedAt(order.getUpdatedAt());
        dto.setUpdatedBy(order.getUpdatedBy());
        return dto;
    }
}