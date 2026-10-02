package com.api.inventory.controller;

import com.api.inventory.dto.MarketplaceDTOs.*;
import com.api.inventory.security.CurrentUser;
import com.api.inventory.service.MarketplaceService;
import com.api.inventory.service.PackageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Joining the marketplace (anyone signed in), and running it (admins with marketplace.manage).
 * Staff who send orders out (orders.fulfil) also work the package board from here.
 */
@RestController
@RequestMapping("/api/marketplace")
public class MarketplaceController {

    private final MarketplaceService marketplace;
    private final PackageService packages;
    private final com.api.inventory.service.PartnerDocumentService documents;

    public MarketplaceController(MarketplaceService marketplace, PackageService packages,
                                 com.api.inventory.service.PartnerDocumentService documents) {
        this.marketplace = marketplace;
        this.packages = packages;
        this.documents = documents;
    }

    // ---------- Public ----------

    /** The cart needs the delivery fee, and the "Deliver with us" page shows what a delivery pays. */
    @GetMapping("/settings")
    public SettingsView settings() {
        return marketplace.settingsView();
    }

    // ---------- Applying ----------

    @GetMapping("/my-applications")
    @PreAuthorize("isAuthenticated()")
    public MyApplications myApplications() {
        return marketplace.myApplications(CurrentUser.email());
    }

    /**
     * The application form as a multipart request: "application" (JSON) plus the documents.
     * The agreement version in the form is recorded as accepted, with the time, IP address and browser.
     */
    @PostMapping(value = "/apply/seller", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public PartnerView applySeller(@RequestPart("application") SellerApplication form,
                                   @RequestPart(value = "idDocument", required = false) org.springframework.web.multipart.MultipartFile idDocument,
                                   @RequestPart(value = "businessDocument", required = false) org.springframework.web.multipart.MultipartFile businessDocument,
                                   jakarta.servlet.http.HttpServletRequest request) {
        return marketplace.applyAsSeller(CurrentUser.email(), form,
                new MarketplaceService.ApplicationFiles(idDocument, null, businessDocument), request);
    }

    @PostMapping(value = "/apply/rider", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public PartnerView applyRider(@RequestPart("application") RiderApplication form,
                                  @RequestPart(value = "idDocument", required = false) org.springframework.web.multipart.MultipartFile idDocument,
                                  @RequestPart(value = "licenceDocument", required = false) org.springframework.web.multipart.MultipartFile licenceDocument,
                                  jakarta.servlet.http.HttpServletRequest request) {
        return marketplace.applyAsRider(CurrentUser.email(), form,
                new MarketplaceService.ApplicationFiles(idDocument, licenceDocument, null), request);
    }

    // ---------- Admin ----------

    @GetMapping("/admin/overview")
    @PreAuthorize("hasAuthority('marketplace.manage')")
    public MarketplaceOverview overview() {
        return marketplace.overview();
    }

    @PutMapping("/admin/settings")
    @PreAuthorize("hasAuthority('marketplace.manage')")
    public SettingsView updateSettings(@RequestBody SettingsRequest request) {
        return marketplace.updateSettings(request, CurrentUser.email());
    }

    @GetMapping("/admin/partners")
    @PreAuthorize("hasAuthority('marketplace.manage')")
    public List<PartnerView> partners() {
        return marketplace.allPartners();
    }

    @PutMapping("/admin/sellers/{id}/status")
    @PreAuthorize("hasAuthority('marketplace.manage')")
    public PartnerView sellerStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        return marketplace.setSellerStatus(id, request, CurrentUser.email());
    }

    @PutMapping("/admin/riders/{id}/status")
    @PreAuthorize("hasAuthority('marketplace.manage')")
    public PartnerView riderStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        return marketplace.setRiderStatus(id, request, CurrentUser.email());
    }

    @PutMapping("/admin/sellers/{id}/commission")
    @PreAuthorize("hasAuthority('marketplace.manage')")
    public PartnerView sellerCommission(@PathVariable Long id, @RequestBody CommissionRequest request) {
        return marketplace.setSellerCommission(id, request);
    }

    @GetMapping("/admin/balances")
    @PreAuthorize("hasAuthority('marketplace.manage')")
    public List<BalanceRow> balances() {
        return marketplace.balances();
    }

    @GetMapping("/admin/ledger/{partyType}/{partyId}")
    @PreAuthorize("hasAuthority('marketplace.manage')")
    public List<LedgerView> ledger(@PathVariable String partyType, @PathVariable Long partyId) {
        return marketplace.ledgerFor(partyType.toUpperCase(), partyId);
    }

    /** Opens a seller's or driver's document (CID, licence...). Admins only; never cached by the browser. */
    @GetMapping("/admin/documents/{id}")
    @PreAuthorize("hasAuthority('marketplace.manage')")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> document(@PathVariable Long id) {
        var d = documents.find(id);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(d.getContentType()))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + d.getKind().toLowerCase() + "-" + d.getId() + "\"")
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(documents.file(d));
    }

    @PostMapping("/admin/adjustments")
    @PreAuthorize("hasAuthority('marketplace.manage')")
    public LedgerView adjustment(@RequestBody AdjustmentRequest request) {
        return marketplace.recordAdjustment(request, CurrentUser.email());
    }

    @PostMapping("/admin/payouts")
    @PreAuthorize("hasAuthority('marketplace.manage')")
    public LedgerView payout(@RequestBody PayoutRequest request) {
        return marketplace.recordPayout(request, CurrentUser.email());
    }

    // ---------- Package board (staff) ----------

    @GetMapping("/packages")
    @PreAuthorize("hasAnyAuthority('orders.view','marketplace.manage')")
    public List<PackageView> allPackages(@RequestParam(required = false) String status) {
        return packages.allForStaff(status);
    }

    @PostMapping("/packages/{id}/packed")
    @PreAuthorize("hasAuthority('orders.fulfil')")
    public PackageView packed(@PathVariable Long id) {
        return packages.markPacked(id);
    }

    @PostMapping("/packages/{id}/pickup")
    @PreAuthorize("hasAuthority('orders.fulfil')")
    public PackageView pickup(@PathVariable Long id) {
        return packages.pickUp(id);
    }

    @PostMapping("/packages/{id}/deliver")
    @PreAuthorize("hasAuthority('orders.fulfil')")
    public PackageView deliver(@PathVariable Long id) {
        return packages.deliver(id, null);
    }
}
