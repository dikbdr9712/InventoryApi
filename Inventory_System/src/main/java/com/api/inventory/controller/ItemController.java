package com.api.inventory.controller;

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
@CrossOrigin(origins = "http://127.0.0.1:5500")
public class ItemController {

    @Autowired  
    private ItemService itemService;
    @Autowired
    private ItemMasterRepository itemMasterRepository; 

    @PostMapping(value = "/addItems", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemMasterDTO createItem(
    	@ModelAttribute ItemMasterDTO dto, 
        @RequestPart(value = "images", required = false) MultipartFile imageFile
    ) {
    	return itemService.createItem(dto, imageFile);
    }

    @GetMapping("/allItems")
    public List<ItemMasterDTO> getAllItems() {
        return itemService.getAllItems();
    }
    
    @GetMapping("/{id}")
    public ItemMasterDTO getItem(@PathVariable Long id) {
        return itemService.getItemById(id);
    }
    
    @DeleteMapping("/{id}")
    public void deleteItem(@PathVariable Long id) {
        itemService.deleteItem(id);
    }
    
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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