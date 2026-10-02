package com.api.inventory.controller;

import com.api.inventory.dto.MarketplaceDTOs.*;
import com.api.inventory.entity.LedgerEntry;
import com.api.inventory.entity.RiderProfile;
import com.api.inventory.service.MarketplaceService;
import com.api.inventory.service.PackageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * The rider's corner: open jobs, their own deliveries, and their money.
 * A rider sees the customer's phone only for jobs they have taken.
 */
@RestController
@RequestMapping("/api/rider")
@PreAuthorize("hasAuthority('rider.portal')")
public class RiderController {

    private final PackageService packages;
    private final MarketplaceService marketplace;

    public RiderController(PackageService packages, MarketplaceService marketplace) {
        this.packages = packages;
        this.marketplace = marketplace;
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        RiderProfile rider = packages.requireApprovedRider();
        Map<String, Object> home = new java.util.LinkedHashMap<>();
        home.put("profile", marketplace.view(rider));
        home.put("earnings", com.api.inventory.security.CurrentUser.has("rider.earnings")
                ? marketplace.earnings(LedgerEntry.RIDER, rider.getId(), packages.rawForRider(rider.getId())) : null);
        return home;
    }

    @PreAuthorize("hasAuthority('rider.jobs')")
    @GetMapping("/jobs")
    public List<PackageView> openJobs() {
        return packages.openJobs(packages.requireApprovedRider());
    }

    @PreAuthorize("hasAuthority('rider.jobs')")
    @GetMapping("/my-jobs")
    public List<PackageView> myJobs() {
        return packages.riderJobs(packages.requireApprovedRider());
    }

    @PreAuthorize("hasAuthority('rider.jobs')")
    @PostMapping("/jobs/{id}/accept")
    public PackageView accept(@PathVariable Long id) {
        return packages.accept(id);
    }

    @PreAuthorize("hasAuthority('rider.jobs')")
    @PostMapping("/jobs/{id}/release")
    public PackageView release(@PathVariable Long id) {
        return packages.release(id);
    }

    @PreAuthorize("hasAuthority('rider.jobs')")
    @PostMapping("/jobs/{id}/pickup")
    public PackageView pickup(@PathVariable Long id) {
        return packages.pickUp(id);
    }

    @PreAuthorize("hasAuthority('rider.jobs')")
    @PostMapping("/jobs/{id}/deliver")
    public PackageView deliver(@PathVariable Long id, @RequestBody(required = false) DeliverRequest request) {
        return packages.deliver(id, request == null ? null : request.code());
    }

    /** A renewed driving licence: new expiry date and a photo or scan of it. */
    @PostMapping(value = "/licence", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public PartnerView renewLicence(@RequestParam("expiry") java.time.LocalDate expiry,
                                    @RequestPart(value = "document", required = false) org.springframework.web.multipart.MultipartFile document) {
        return marketplace.renewLicence(com.api.inventory.security.CurrentUser.email(), expiry, document);
    }

    @PreAuthorize("hasAuthority('rider.earnings')")
    @GetMapping("/ledger")
    public List<LedgerView> ledger() {
        return marketplace.ledgerFor(LedgerEntry.RIDER, packages.requireApprovedRider().getId());
    }
}
