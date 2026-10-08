package com.api.inventory.controller;

import com.api.inventory.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import com.api.inventory.dto.ItemMasterDTO;
import com.api.inventory.entity.ItemMaster;
import com.api.inventory.repository.ItemMasterRepository;
import com.api.inventory.service.ItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.api.inventory.entity.SellerProfile;
import com.api.inventory.repository.SellerProfileRepository;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    @Autowired
    private ItemService itemService;
    @Autowired
    private ItemMasterRepository itemMasterRepository;
    @Autowired
    private SellerProfileRepository sellerProfileRepository;

    @PostMapping(value = "/addItems", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('items.manage')")
    public ItemMasterDTO createItem(
            @ModelAttribute ItemMasterDTO dto,
            @RequestPart(value = "images", required = false) MultipartFile imageFile
    ) {
        return itemService.createItem(dto, imageFile);
    }

    @GetMapping("/allItems")
    public List<ItemMasterDTO> getAllItems() {
        Map<Long, SellerProfile> sellers = sellerProfileRepository.findAll().stream()
                .collect(Collectors.toMap(SellerProfile::getId, s -> s));
        boolean staff = CurrentUser.has("items.manage");
        List<ItemMasterDTO> items = itemService.getAllItems().stream()
                // shoppers only see marketplace products that are switched on and whose seller is active
                .filter(item -> staff || item.getSellerId() == null
                        || (!Boolean.FALSE.equals(item.getIsActive())
                            && sellers.containsKey(item.getSellerId()) && sellers.get(item.getSellerId()).isApproved()))
                .collect(Collectors.toList());
        items.forEach(item -> withSeller(forCaller(item), sellers));
        return items;
    }

    @GetMapping("/{id}")
    public ItemMasterDTO getItem(@PathVariable Long id) {
        ItemMasterDTO item = forCaller(itemService.getItemById(id));
        if (item != null && item.getSellerId() != null) {
            sellerProfileRepository.findById(item.getSellerId()).ifPresent(s -> item.setSellerName(s.getShopName()));
        }
        if (item != null) {
            item.setPhotos(photos.list(id)); // the product page's gallery
        }
        return item;
    }

    // ---------- more photos (at most 5 in all), deals and featured products ----------
    @Autowired
    private com.api.inventory.service.ItemPhotoService photos;

    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('items.manage')")
    public List<com.api.inventory.service.ItemPhotoService.PhotoView> addPhoto(@PathVariable Long id, @RequestPart("photo") MultipartFile photo) {
        return photos.add(id, photo);
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    @PreAuthorize("hasAuthority('items.manage')")
    public List<com.api.inventory.service.ItemPhotoService.PhotoView> removePhoto(@PathVariable Long id, @PathVariable Long photoId) {
        return photos.remove(id, photoId);
    }

    @PostMapping("/{id}/photos/{photoId}/main")
    @PreAuthorize("hasAuthority('items.manage')")
    public List<com.api.inventory.service.ItemPhotoService.PhotoView> mainPhoto(@PathVariable Long id, @PathVariable Long photoId) {
        return photos.makeMain(id, photoId);
    }

    /** highlight: DEAL, FEATURED or empty (not on the home page); dealEndsAt: when a deal stops (empty = until changed). */
    public record Highlight(String highlight, java.time.Instant dealEndsAt) {
    }

    @PutMapping("/{id}/highlight")
    @PreAuthorize("hasAuthority('offers.manage')")
    public ItemMasterDTO highlight(@PathVariable Long id, @RequestBody Highlight body) {
        ItemMaster item = itemMasterRepository.findById(id).orElseThrow(() -> new IllegalStateException("Product not found."));
        String h = body == null || body.highlight() == null ? "" : body.highlight().trim().toUpperCase();
        if (!h.isEmpty() && !ItemMaster.DEAL.equals(h) && !ItemMaster.FEATURED.equals(h)) {
            throw new IllegalArgumentException("Choose a deal, featured, or neither.");
        }
        if (ItemMaster.DEAL.equals(h) && body.dealEndsAt() != null && body.dealEndsAt().isBefore(java.time.Instant.now())) {
            throw new IllegalArgumentException("The deal's end is already past. Choose a later time.");
        }
        item.setHighlight(h.isEmpty() ? null : h);
        item.setDealEndsAt(ItemMaster.DEAL.equals(h) ? body.dealEndsAt() : null);
        itemMasterRepository.save(item);
        return getItem(id);
    }

    /** "Sold by ..." on marketplace products. Our own products have no seller name. */
    private void withSeller(ItemMasterDTO item, Map<Long, SellerProfile> sellers) {
        if (item.getSellerId() != null && sellers.containsKey(item.getSellerId())) {
            item.setSellerName(sellers.get(item.getSellerId()).getShopName());
        }
    }

    /**
     * The product list is public, so it must not show what the shop paid, the markup, or who supplies it.
     * Only people who manage products see those.
     */
    private ItemMasterDTO forCaller(ItemMasterDTO item) {
        if (item != null && !CurrentUser.has("items.manage")) {
            item.setCostPrice(null);
            item.setMarkupPercent(null);
            item.setSupplierItemCode(null);
        }
        return item;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('items.manage')")
    public void deleteItem(@PathVariable Long id) {
        itemService.deleteItem(id);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('items.manage')")
    public ItemMasterDTO updateItem(
            @PathVariable Long id,
            @ModelAttribute ItemMasterDTO dto,
            @RequestParam(value = "image", required = false) MultipartFile imageFile  // ✅ 3rd param
    ) {
        return itemService.updateItem(id, dto, imageFile);  // ← Needs 3 params
    }

    @GetMapping("/stock/{itemId}")
    public Integer getItemStock(@PathVariable Long itemId) {
        return itemService.getItemStock(itemId);
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyAuthority('stock.restock','items.manage')")
    public List<ItemMaster> searchItems(@RequestParam String term) {
        if (term == null || term.trim().isEmpty()) {
            return List.of();
        }
        String cleanTerm = term.trim();
        // ✅ Use the injected bean: itemMasterRepository (lowercase 'i')
        return itemMasterRepository.findBySkuContainingIgnoreCaseOrItemNameContainingIgnoreCase(cleanTerm, cleanTerm);
    }

    public ItemMasterRepository getItemMasterRepository() {
        return itemMasterRepository;
    }

    public void setItemMasterRepository(ItemMasterRepository itemMasterRepository) {
        this.itemMasterRepository = itemMasterRepository;
    }
}