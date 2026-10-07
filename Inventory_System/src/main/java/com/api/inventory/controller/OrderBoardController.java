package com.api.inventory.controller;

import com.api.inventory.dto.MarketplaceDTOs.PackageView;
import com.api.inventory.service.OrderBoardService;
import com.api.inventory.service.PackageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * The order board (page /admin/orders): every online order by step, who has it, what is late, and the team.
 *   GET  /api/admin/order-board?from=&to=&older=        the board for orders placed in a period (days, both included;
 *                                                        this month when empty); older=true adds earlier orders still open
 *   POST /api/admin/order-board/packages/{id}/take       "Take it": I pack it                 (orders.fulfil)
 *   POST /api/admin/order-board/packages/{id}/release    "Give back"                          (orders.fulfil: own, or orders.assign)
 *   POST /api/admin/order-board/packages/{id}/packer     {email}: give it to someone to pack  (orders.assign)
 *   POST /api/admin/order-board/packages/{id}/rider      {riderId}: give it to a driver       (orders.assign)
 *   POST /api/admin/order-board/packages/{id}/rider/remove  take it off the driver           (orders.assign)
 * Packed / We deliver it / Delivered stay at /api/marketplace/packages/{id}/... (orders.fulfil).
 */
@RestController
@RequestMapping("/api/admin/order-board")
public class OrderBoardController {

    private final OrderBoardService board;
    private final PackageService packages;

    public OrderBoardController(OrderBoardService board, PackageService packages) {
        this.board = board;
        this.packages = packages;
    }

    public record PackerRequest(String email) {
    }

    public record RiderRequest(Long riderId) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('orders.view')")
    public OrderBoardService.Board board(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            @RequestParam(defaultValue = "false") boolean older) {
        return board.board(from, to, older);
    }

    /** "Pick up myself": the customer came to collect it. The collection code may be left out (staff checked who it is). */
    @PostMapping("/packages/{id}/handover")
    @PreAuthorize("hasAuthority('orders.fulfil')")
    public PackageView handOver(@PathVariable Long id, @RequestBody(required = false) com.api.inventory.dto.MarketplaceDTOs.DeliverRequest request) {
        return packages.handOver(id, request == null ? null : request.code());
    }

    @PostMapping("/packages/{id}/take")
    @PreAuthorize("hasAuthority('orders.fulfil')")
    public PackageView take(@PathVariable Long id) {
        return packages.takePacking(id);
    }

    @PostMapping("/packages/{id}/release")
    @PreAuthorize("hasAuthority('orders.fulfil')")
    public PackageView release(@PathVariable Long id) {
        return packages.releasePacking(id);
    }

    @PostMapping("/packages/{id}/packer")
    @PreAuthorize("hasAuthority('orders.assign')")
    public PackageView packer(@PathVariable Long id, @RequestBody PackerRequest request) {
        return packages.assignPacker(id, request.email());
    }

    @PostMapping("/packages/{id}/rider")
    @PreAuthorize("hasAuthority('orders.assign')")
    public PackageView rider(@PathVariable Long id, @RequestBody RiderRequest request) {
        return packages.assignRider(id, request.riderId());
    }

    @PostMapping("/packages/{id}/rider/remove")
    @PreAuthorize("hasAuthority('orders.assign')")
    public PackageView removeRider(@PathVariable Long id) {
        return packages.removeRider(id);
    }
}
