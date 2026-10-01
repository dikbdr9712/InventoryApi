package com.api.inventory.controller;

import com.api.inventory.dto.ReturnDTO.ReturnRequest;
import com.api.inventory.dto.ReturnDTO.ReturnView;
import com.api.inventory.dto.ReturnDTO.ReturnableOrder;
import com.api.inventory.service.SalesReturnService;
import com.api.inventory.service.SalesReturnService.ReturnException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Returns of items from an earlier sale.
 *
 * Who is calling is read from the login session (the same "userEmail" and "userRole" that AuthController stores),
 * so it is checked on the server and cannot be skipped by calling the URL directly.
 */
@RestController
@RequestMapping("/api/orders/{orderId}")
public class SalesReturnController {

    /** These roles may look at returns. */
    private static final Set<String> VIEW_ROLES = Set.of("ADMIN", "MANAGER", "CONTROLLER");

    private final SalesReturnService service;

    /** These roles may make a return. Change with app.returns.roles (a comma-separated list). */
    @Value("${app.returns.roles:ADMIN,MANAGER}")
    private String createRoles;

    public SalesReturnController(SalesReturnService service) {
        this.service = service;
    }

    /** What can still be returned from this sale, and what each unit would refund. */
    @GetMapping("/returnable")
    public ReturnableOrder returnable(@PathVariable("orderId") Long orderId, HttpServletRequest request) {
        signedInWithRole(request, VIEW_ROLES);
        return service.returnable(orderId);
    }

    /** The returns already made against this sale. */
    @GetMapping("/returns")
    public List<ReturnView> list(@PathVariable("orderId") Long orderId, HttpServletRequest request) {
        signedInWithRole(request, VIEW_ROLES);
        return service.list(orderId);
    }

    /** Takes items back, gives the money back, and puts stock back on the shelf. */
    @PostMapping("/returns")
    public ReturnView create(@PathVariable("orderId") Long orderId, @RequestBody ReturnRequest body,
                             HttpServletRequest request) {
        String email = signedInWithRole(request, rolesFrom(createRoles));
        return service.createReturn(orderId, body, email);
    }

    /** Turns a ReturnException into a clear message the screen can show. */
    @ExceptionHandler(ReturnException.class)
    public ResponseEntity<Map<String, String>> handle(ReturnException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }

    /** Returns the signed-in person's email, or refuses. */
    private String signedInWithRole(HttpServletRequest request, Set<String> allowed) {
        HttpSession session = request.getSession(false);
        String email = session == null ? null : (String) session.getAttribute("userEmail");
        String role = session == null ? null : (String) session.getAttribute("userRole");
        if (email == null || role == null) {
            throw new ReturnException(HttpStatus.UNAUTHORIZED, "Please sign in.");
        }
        if (!allowed.contains(role.trim().toUpperCase())) {
            throw new ReturnException(HttpStatus.FORBIDDEN, "Your account is not allowed to do this.");
        }
        return email;
    }

    private static Set<String> rolesFrom(String commaSeparated) {
        return Arrays.stream(commaSeparated.split(","))
                .map(s -> s.trim().toUpperCase())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}