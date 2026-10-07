package com.api.inventory.controller;

import com.api.inventory.dto.ItemMasterDTO;
import com.api.inventory.dto.MarketplaceDTOs.*;
import com.api.inventory.entity.LedgerEntry;
import com.api.inventory.entity.SellerProfile;
import com.api.inventory.service.MarketplaceService;
import com.api.inventory.service.PackageService;
import com.api.inventory.service.SellerItemService;
import com.api.inventory.service.SellerItemService.SellerItemForm;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * The seller's own corner: their shop, their products, the packages to pack, and their money.
 * Every call works on the signed-in seller's own data only.
 */
@RestController
@RequestMapping("/api/seller")
@PreAuthorize("hasAuthority('seller.portal')")
public class SellerController {

    private final PackageService packages;
    private final MarketplaceService marketplace;
    private final SellerItemService sellerItems;

    public SellerController(PackageService packages, MarketplaceService marketplace, SellerItemService sellerItems) {
        this.packages = packages;
        this.marketplace = marketplace;
        this.sellerItems = sellerItems;
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        SellerProfile seller = packages.requireApprovedSeller();
        Map<String, Object> home = new java.util.LinkedHashMap<>();
        home.put("profile", marketplace.view(seller));
        // money only for people allowed to see it (for example not a packing helper)
        home.put("earnings", com.api.inventory.security.CurrentUser.has("seller.earnings")
                ? marketplace.earnings(LedgerEntry.SELLER, seller.getId(), packages.rawForSeller(seller.getId())) : null);
        return home;
    }

    /** The shop's pickup point on the map, so delivery distances (and prices) are exact. */
    @PutMapping("/location")
    public PartnerView setLocation(@RequestBody com.api.inventory.dto.DeliveryDTOs.LocationRequest request) {
        return marketplace.setPickupLocation(packages.requireApprovedSeller(), request);
    }

    @PreAuthorize("hasAuthority('seller.orders')")
    @GetMapping("/packages")
    public List<PackageView> myPackages() {
        return packages.sellerPackages(packages.requireApprovedSeller());
    }

    @PreAuthorize("hasAuthority('seller.orders')")
    @PostMapping("/packages/{id}/packed")
    public PackageView packed(@PathVariable Long id) {
        return packages.markPacked(id);
    }

    /** "Pick up myself": the customer collects it at the seller's; the seller types the customer's collection code. */
    @PreAuthorize("hasAuthority('seller.orders')")
    @PostMapping("/packages/{id}/handover")
    public PackageView handOver(@PathVariable Long id, @RequestBody DeliverRequest request) {
        return packages.handOver(id, request == null ? null : request.code());
    }

    @PreAuthorize("hasAuthority('seller.earnings')")
    @GetMapping("/ledger")
    public List<LedgerView> ledger() {
        return marketplace.ledgerFor(LedgerEntry.SELLER, packages.requireApprovedSeller().getId());
    }

    @PreAuthorize("hasAuthority('seller.products')")
    @GetMapping("/items")
    public List<ItemMasterDTO> myItems() {
        return sellerItems.list(packages.requireApprovedSeller());
    }

    @PreAuthorize("hasAuthority('seller.products')")
    @PostMapping(value = "/items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemMasterDTO addItem(@ModelAttribute SellerItemForm form,
                                 @RequestPart(value = "image", required = false) MultipartFile image) {
        return sellerItems.create(packages.requireApprovedSeller(), form, image);
    }

    @PreAuthorize("hasAuthority('seller.products')")
    @PutMapping(value = "/items/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemMasterDTO updateItem(@PathVariable Long id, @ModelAttribute SellerItemForm form,
                                    @RequestPart(value = "image", required = false) MultipartFile image) {
        return sellerItems.update(packages.requireApprovedSeller(), id, form, image);
    }
}
