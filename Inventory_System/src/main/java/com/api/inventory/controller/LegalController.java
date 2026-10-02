package com.api.inventory.controller;

import com.api.inventory.entity.LegalTerms;
import com.api.inventory.entity.TermsAcceptance;
import com.api.inventory.repository.RiderProfileRepository;
import com.api.inventory.repository.SellerProfileRepository;
import com.api.inventory.security.CurrentUser;
import com.api.inventory.service.LegalTermsService;
import com.api.inventory.service.LegalTermsService.PublishRequest;
import com.api.inventory.service.LegalTermsService.TermsStatus;
import com.api.inventory.service.LegalTermsService.TermsView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agreements: anyone can read them (before signing up), signed-in people accept them,
 * and admins publish new versions. Types: SELLER, RIDER (also "DRIVER"), CUSTOMER.
 */
@RestController
@RequestMapping("/api/legal")
public class LegalController {

    private final LegalTermsService legal;
    private final SellerProfileRepository sellers;
    private final RiderProfileRepository riders;

    public LegalController(LegalTermsService legal, SellerProfileRepository sellers, RiderProfileRepository riders) {
        this.legal = legal;
        this.sellers = sellers;
        this.riders = riders;
    }

    // ---------- public ----------

    @GetMapping("/terms/{type}")
    public TermsView current(@PathVariable String type) {
        return legal.currentView(type);
    }

    /** An older version, so a person can see exactly what they agreed to. */
    @GetMapping("/terms/{type}/versions/{version}")
    public TermsView version(@PathVariable String type, @PathVariable int version) {
        return legal.version(type, version).orElseThrow(() -> new IllegalStateException("That version does not exist."));
    }

    // ---------- signed in ----------

    @GetMapping("/terms/{type}/status")
    @PreAuthorize("isAuthenticated()")
    public TermsStatus status(@PathVariable String type) {
        return legal.status(CurrentUser.email(), type);
    }

    public record AcceptRequest(Integer version) {
    }

    public record AcceptedView(String type, Integer version, Instant acceptedAt) {
    }

    @PostMapping("/terms/{type}/accept")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public AcceptedView accept(@PathVariable String type, @RequestBody AcceptRequest body, HttpServletRequest request) {
        String email = CurrentUser.email();
        TermsAcceptance a = legal.accept(email, type, body == null ? null : body.version(), request);
        // keep the seller/driver account up to date with the version they now work under
        if (LegalTerms.SELLER.equals(a.getTermsType())) {
            sellers.findByUserEmail(email).ifPresent(s -> {
                s.setTermsVersion(a.getVersion());
                s.setTermsAcceptedAt(a.getAcceptedAt());
                sellers.save(s);
            });
        } else if (LegalTerms.RIDER.equals(a.getTermsType())) {
            riders.findByUserEmail(email).ifPresent(r -> {
                r.setTermsVersion(a.getVersion());
                r.setTermsAcceptedAt(a.getAcceptedAt());
                riders.save(r);
            });
        }
        return new AcceptedView(a.getTermsType(), a.getVersion(), a.getAcceptedAt());
    }

    // ---------- admin ----------

    /** Every agreement with all its versions (newest first) and how many people accepted each. */
    @GetMapping("/admin/terms")
    @PreAuthorize("hasAnyAuthority('marketplace.manage','users.manage')")
    public Map<String, List<TermsView>> all() {
        Map<String, List<TermsView>> result = new LinkedHashMap<>();
        for (String type : List.of(LegalTerms.SELLER, LegalTerms.RIDER, LegalTerms.CUSTOMER)) {
            result.put(type, legal.history(type));
        }
        return result;
    }

    /** Publishes a new version. Sellers/drivers must accept it before they continue; it never edits an old version. */
    @PostMapping("/admin/terms/{type}")
    @PreAuthorize("hasAnyAuthority('marketplace.manage','users.manage')")
    public TermsView publish(@PathVariable String type, @RequestBody PublishRequest request) {
        return legal.publish(type, request);
    }
}
