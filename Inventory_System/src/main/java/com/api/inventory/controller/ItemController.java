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
        List<ItemMasterDTO> items = itemService.getAllItems();
        items.forEach(this::forCaller);
        return items;
    }

    @GetMapping("/{id}")
    public ItemMasterDTO getItem(@PathVariable Long id) {
        return forCaller(itemService.getItemById(id));
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