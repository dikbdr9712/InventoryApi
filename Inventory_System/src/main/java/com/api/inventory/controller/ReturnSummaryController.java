package com.api.inventory.controller;

import com.api.inventory.service.SalesReturnService.ReturnException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One answer for the Sales history list: which sales have had refunds, how much, and whether it is all of it.
 * GET /api/returns/summary
 */
@RestController
@RequestMapping("/api/returns")
public class ReturnSummaryController {

    /** These roles may look at returns (the same as in SalesReturnController). */
    private static final Set<String> VIEW_ROLES = Set.of("ADMIN", "MANAGER", "CONTROLLER");
    private static final BigDecimal TOLERANCE = new BigDecimal("0.05"); // a few cents of rounding

    /** A sale that has had at least one return. */
    public record SaleRefund(Long orderId, long returnCount, BigDecimal refundedAmount, BigDecimal paidTotal,
                             boolean fullyReturned) {
    }

    @PersistenceContext
    private EntityManager em;

    @GetMapping("/summary")
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('sales.return','pos.use','orders.view','reports.view')")
    public List<SaleRefund> summary() {

        List<Object[]> rows = em.createNativeQuery(
                        "SELECT r.order_id, COUNT(*), SUM(r.refund_amount), o.total_amount "
                                + "FROM sales_returns r JOIN orders o ON o.order_id = r.order_id "
                                + "GROUP BY r.order_id, o.total_amount "
                                + "ORDER BY r.order_id")
                .getResultList();

        List<SaleRefund> result = new ArrayList<>();
        for (Object[] row : rows) {
            BigDecimal refunded = money(row[2]);
            BigDecimal paid = money(row[3]);
            result.add(new SaleRefund(((Number) row[0]).longValue(), ((Number) row[1]).longValue(), refunded, paid,
                    paid.subtract(refunded).compareTo(TOLERANCE) <= 0));
        }
        return result;
    }

    @ExceptionHandler(ReturnException.class)
    public ResponseEntity<Map<String, String>> handle(ReturnException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }

    private void requireStaff(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String email = session == null ? null : (String) session.getAttribute("userEmail");
        String role = session == null ? null : (String) session.getAttribute("userRole");
        if (email == null || role == null) {
            throw new ReturnException(HttpStatus.UNAUTHORIZED, "Please sign in.");
        }
        if (!VIEW_ROLES.contains(role.trim().toUpperCase())) {
            throw new ReturnException(HttpStatus.FORBIDDEN, "Your account is not allowed to do this.");
        }
    }

    private static BigDecimal money(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }
}
