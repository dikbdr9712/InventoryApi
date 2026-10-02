package com.api.inventory.service.impl;

import com.api.inventory.dto.ItemMasterDTO;
import com.api.inventory.entity.InventoryStock;
import com.api.inventory.entity.ItemMaster;
import com.api.inventory.repository.InventoryStockRepository;
import com.api.inventory.repository.ItemMasterRepository;
import com.api.inventory.service.ItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.math.BigDecimal;

@Service
public class ItemServiceImpl implements ItemService {

    @Autowired
    private ItemMasterRepository itemMasterRepository;

    @Autowired
    private InventoryStockRepository inventoryStockRepository;

    @Override
    @Transactional
    public ItemMasterDTO createItem(ItemMasterDTO dto) {
        if (dto.getItemName() == null || dto.getItemName().trim().isEmpty()) {
            throw new IllegalArgumentException("Item name is required");
        }

        ItemMaster item = new ItemMaster();
        item.setItemName(dto.getItemName().trim());
        item.setDescription(dto.getDescription());
        item.setUom(dto.getUom() != null ? dto.getUom().trim() : "pcs");
        item.setCostPrice(dto.getCostPrice());
        item.setSellingPrice(dto.getSellingPrice());
        item.setMrp(dto.getMrp() != null ? dto.getMrp() : BigDecimal.ZERO);
        item.setBarcode(dto.getBarcode());
        item.setSupplierItemCode(dto.getSupplierItemCode());
        item.setCreatedAt(LocalDateTime.now());
        item.setCategory(dto.getCategory().trim());
        item.setDeliverySize(com.api.inventory.entity.DeliverySize.parse(dto.getDeliverySize()));

        // ✅ Generate SKU BEFORE saving
        String autoSku = "ITEM-" + System.currentTimeMillis(); // Temporary unique ID
        item.setSku(autoSku);

        // ✅ Save with SKU already set
        ItemMaster savedItem = itemMasterRepository.save(item);

        // ✅ Now update with real ID-based SKU (optional)
        String finalSku = "ITEM-" + savedItem.getItemId();
        savedItem.setSku(finalSku);
        itemMasterRepository.save(savedItem); // Update again

        // Create stock record
        InventoryStock stock = new InventoryStock();
        stock.setItemId(savedItem.getItemId());
        stock.setCurrentQuantity(0);
        stock.setLastUpdated(LocalDateTime.now());
        stock.setStatus("Unavailable");
        inventoryStockRepository.save(stock);

        return toDTO(savedItem, stock);
    }

    @Override
    public List<ItemMasterDTO> getAllItems() {
        return itemMasterRepository.findAll().stream()
            .map(item -> {
                InventoryStock stock = inventoryStockRepository.findByItemId(item.getItemId())
                    .orElse(null); // ← Get full InventoryStock object
                return toDTO(item, stock);
            })
            .collect(Collectors.toList());
    }

    private ItemMasterDTO toDTO(ItemMaster item, InventoryStock stock) {
        ItemMasterDTO dto = new ItemMasterDTO();
        
        // Item fields
        dto.setItemId(item.getItemId());
        dto.setSku(item.getSku());
        dto.setItemName(item.getItemName());
        dto.setDescription(item.getDescription());
        dto.setUom(item.getUom());
        dto.setCostPrice(item.getCostPrice());
        dto.setSellingPrice(item.getSellingPrice());
        dto.setMrp(item.getMrp());                    // ✅ For discount calculation
        dto.setMarkupPercent(item.getMarkupPercent()); // ✅ For analytics
        dto.setCategory(item.getCategory());
        dto.setBarcode(item.getBarcode());
        dto.setSupplierItemCode(item.getSupplierItemCode());
        dto.setTaxRate(item.getTaxRate());
        dto.setDiscountAllowed(item.getDiscountAllowed());
        dto.setMaxDiscountPercent(item.getMaxDiscountPercent());
        dto.setImagePath(item.getImagePath());
        dto.setIsActive(item.getIsActive());
        dto.setCreatedAt(item.getCreatedAt());
        dto.setSellerId(item.getSellerId());
        dto.setDeliverySize(com.api.inventory.entity.DeliverySize.of(item.getDeliverySize()).name());

        // Stock fields (from inventory_stock table)
        if (stock != null) {
            dto.setStockId(stock.getStockId());
            dto.setCurrentQuantity(stock.getCurrentQuantity());
            dto.setStatus(stock.getStatus());
            dto.setAvailability(stock.getStatus()); // For frontend compatibility
        }
        
        return dto;
    }

    @Override
    @Transactional
    public void deleteItem(Long id) {
        itemMasterRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Item not found with ID: " + id));

        itemMasterRepository.deleteById(id);
        

    }

    @Override
    @Transactional
    public ItemMasterDTO updateItem(Long id, ItemMasterDTO dto) {
        // ✅ Find existing item
        ItemMaster existingItem = itemMasterRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Item not found with ID: " + id));

        // ✅ Update basic fields
        existingItem.setItemName(dto.getItemName() != null ? dto.getItemName().trim() : existingItem.getItemName());
        existingItem.setDescription(dto.getDescription());
        existingItem.setUom(dto.getUom() != null ? dto.getUom().trim() : existingItem.getUom());
        existingItem.setCategory(dto.getCategory() != null ? dto.getCategory().trim() : existingItem.getCategory());
        
        // ✅ Update pricing fields
        existingItem.setCostPrice(dto.getCostPrice() != null ? dto.getCostPrice() : existingItem.getCostPrice());
        existingItem.setSellingPrice(dto.getSellingPrice() != null ? dto.getSellingPrice() : existingItem.getSellingPrice());
        
        // ✅ MRP: Use DTO value if provided and > 0, otherwise default to selling price (matches createItem logic)
        if (dto.getMrp() != null && dto.getMrp().compareTo(BigDecimal.ZERO) > 0) {
            existingItem.setMrp(dto.getMrp());
        } else {
            existingItem.setMrp(existingItem.getSellingPrice());
        }
        
        // ✅ Markup Percent: Update if provided
        if (dto.getMarkupPercent() != null) {
            existingItem.setMarkupPercent(dto.getMarkupPercent());
        }
        
        // ✅ Update optional fields
        existingItem.setBarcode(dto.getBarcode());
        existingItem.setSupplierItemCode(dto.getSupplierItemCode());
        existingItem.setTaxRate(dto.getTaxRate() != null ? dto.getTaxRate() : existingItem.getTaxRate());
        existingItem.setDiscountAllowed(dto.getDiscountAllowed() != null ? dto.getDiscountAllowed() : existingItem.getDiscountAllowed());
        existingItem.setMaxDiscountPercent(dto.getMaxDiscountPercent() != null ? dto.getMaxDiscountPercent() : existingItem.getMaxDiscountPercent());
        existingItem.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : existingItem.getIsActive());
        if (dto.getDeliverySize() != null) {
            existingItem.setDeliverySize(com.api.inventory.entity.DeliverySize.parse(dto.getDeliverySize()));
        }

        // ✅ Save updated item
        ItemMaster updatedItem = itemMasterRepository.save(existingItem);
        
        // ✅ Handle stock quantity update (if provided in DTO)
        InventoryStock stock = inventoryStockRepository.findByItemId(id)
            .orElse(null);
        
        if (stock != null && dto.getQuantity() != null) {
            stock.setCurrentQuantity(dto.getQuantity());
            stock.setLastUpdated(LocalDateTime.now());
            stock.setStatus(dto.getQuantity() > 0 ? "Available" : "Unavailable");
            inventoryStockRepository.save(stock);
        }
        
        // ✅ Debug log
        System.out.println("✏️ Item updated: ID=" + updatedItem.getItemId() + 
                           ", MRP=" + updatedItem.getMrp() + 
                           ", Selling=" + updatedItem.getSellingPrice() +
                           ", Discount=" + (updatedItem.getMrp().compareTo(updatedItem.getSellingPrice()) > 0 ? 
                               Math.round(((updatedItem.getMrp().doubleValue() - updatedItem.getSellingPrice().doubleValue()) / 
                               updatedItem.getMrp().doubleValue()) * 100) + "%" : "none"));

        return toDTO(updatedItem, stock);
    }

	@Override
	public Integer getItemStock(Long itemId) {
	    System.out.println("Fetching stock for itemId: " + itemId); // 👈 DEBUG

	    Optional<InventoryStock> stockOpt = inventoryStockRepository.findByItemId(itemId);
	    
	    if (stockOpt.isPresent()) {
	        int qty = stockOpt.get().getCurrentQuantity();
	        System.out.println("Found stock: " + qty); // 👈 DEBUG
	        return qty;
	    } else {
	        System.out.println("No stock found for itemId: " + itemId); // 👈 DEBUG
	        return 0;
	    }
	}

	@Override
	@Transactional
	public ItemMasterDTO createItem(ItemMasterDTO dto, MultipartFile imageFile) {
	    // ✅ Validate required fields
	    if (dto.getItemName() == null || dto.getItemName().trim().isEmpty()) {
	        throw new IllegalArgumentException("Item name is required");
	    }

	    // ✅ Create and populate ItemMaster entity
	    ItemMaster item = new ItemMaster();
	    item.setItemName(dto.getItemName().trim());
	    item.setDescription(dto.getDescription());
	    item.setUom(dto.getUom() != null ? dto.getUom().trim() : "pcs");
	    
	    // ✅ Pricing: Cost, Selling, MRP, Markup
	    item.setCostPrice(dto.getCostPrice() != null ? dto.getCostPrice() : BigDecimal.ZERO);
	    
	    // Selling price: use DTO value or fallback to cost price
	    item.setSellingPrice(
	        dto.getSellingPrice() != null ? dto.getSellingPrice() : item.getCostPrice()
	    );
	    
	    // MRP: use DTO value if provided and > 0, otherwise default to selling price (no discount)
	    if (dto.getMrp() != null && dto.getMrp().compareTo(BigDecimal.ZERO) > 0) {
	        item.setMrp(dto.getMrp());
	    } else {
	        item.setMrp(item.getSellingPrice());
	    }
	    
	    // Markup percent: optional, for analytics/reporting
	    if (dto.getMarkupPercent() != null) {
	        item.setMarkupPercent(dto.getMarkupPercent());
	    }
	    
	    // Category with default fallback
	    item.setCategory(dto.getCategory() != null ? dto.getCategory().trim() : "Uncategorized");
	    item.setDeliverySize(com.api.inventory.entity.DeliverySize.parse(dto.getDeliverySize()));
	    
	    // ✅ Optional fields with null-safe defaults
	    item.setBarcode(dto.getBarcode());
	    item.setSupplierItemCode(dto.getSupplierItemCode());
	    item.setDiscountAllowed(dto.getDiscountAllowed() != null ? dto.getDiscountAllowed() : true);
	    item.setMaxDiscountPercent(
	        dto.getMaxDiscountPercent() != null ? dto.getMaxDiscountPercent() : new BigDecimal("100.00")
	    );
	    item.setTaxRate(dto.getTaxRate() != null ? dto.getTaxRate() : BigDecimal.ZERO);
	    item.setIsActive(true); // Default to active

	    // ✅ Generate temporary SKU, save, then update with permanent SKU
	    item.setSku("TEMP-" + System.currentTimeMillis());
	    ItemMaster savedItem = itemMasterRepository.save(item);

	    String finalSku = "ITEM-" + savedItem.getItemId();
	    savedItem.setSku(finalSku);
	    itemMasterRepository.save(savedItem);

	    // ✅ Handle image upload (optional)
	    if (imageFile != null && !imageFile.isEmpty()) {
	        try {
	            String cleanName = savedItem.getItemName().toLowerCase().replaceAll("[^a-z0-9]", "");
	            cleanName = cleanName.substring(0, Math.min(50, cleanName.length()));
	            String filename = cleanName + ".jpg";

	            Path uploadDir = Paths.get(System.getProperty("user.dir"), "uploads");
	            Files.createDirectories(uploadDir);
	            Path filePath = uploadDir.resolve(filename);
	            
	            Files.copy(imageFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

	            savedItem.setImagePath("/uploads/" + filename);
	            itemMasterRepository.save(savedItem);
	            System.out.println("✅ Saved item with imagePath: " + savedItem.getImagePath());
	            
	        } catch (IOException e) {
	            System.err.println("❌ Failed to save image: " + e.getMessage());
	            // Continue without image - don't fail the whole operation
	        }
	    }

	    // ✅ Create inventory_stock record with quantity from DTO (NOT hardcoded 0)
	    Integer initialQuantity = dto.getQuantity() != null ? dto.getQuantity() : 0;
	    
	    InventoryStock stock = new InventoryStock();
	    stock.setItemId(savedItem.getItemId());
	    stock.setCurrentQuantity(initialQuantity);  // ✅ Use actual quantity from frontend
	    stock.setLastUpdated(LocalDateTime.now());
	    
	    // Set status based on quantity (or let @PrePersist handle it)
	    stock.setStatus(initialQuantity > 0 ? "Available" : "Unavailable");
	    
	    inventoryStockRepository.save(stock);
	    
	    // ✅ Debug log (remove in production or use proper logger)
	    System.out.println("📦 Item created: ID=" + savedItem.getItemId() + 
	                       ", Qty=" + initialQuantity + 
	                       ", MRP=" + item.getMrp() + 
	                       ", Selling=" + item.getSellingPrice() +
	                       ", Discount=" + (item.getMrp().compareTo(item.getSellingPrice()) > 0 ? 
	                           Math.round(((item.getMrp().doubleValue() - item.getSellingPrice().doubleValue()) / 
	                           item.getMrp().doubleValue()) * 100) + "%" : "none"));

	    // ✅ Return DTO with item + stock info for frontend
	    return toDTO(savedItem, stock);
	}

	@Transactional
	public ItemMasterDTO updateItem(Long itemId, ItemMasterDTO dto, MultipartFile imageFile) {
	    ItemMaster item = itemMasterRepository.findById(itemId)
	        .orElseThrow(() -> new IllegalArgumentException("Item not found"));

	    // Update basic fields
	    item.setItemName(dto.getItemName().trim());
	    item.setDescription(dto.getDescription());
	    item.setUom(dto.getUom() != null ? dto.getUom().trim() : "pcs");
	    item.setSellingPrice(dto.getSellingPrice());
	    item.setBarcode(dto.getBarcode());
	    item.setSupplierItemCode(dto.getSupplierItemCode());
	    if (dto.getDeliverySize() != null) {
	        item.setDeliverySize(com.api.inventory.entity.DeliverySize.parse(dto.getDeliverySize()));
	    }

	    // Handle image upload
	    if (imageFile != null && !imageFile.isEmpty()) {
	        try {
	            // Clean filename
	            String rawName = item.getItemName();
	            String cleanName = rawName
	                .toLowerCase()
	                .replaceAll("[^a-z0-9]", "");
	            cleanName = cleanName.substring(0, Math.min(50, cleanName.length()));
	            String filename = cleanName + ".jpg";

	            // Save to "uploads" folder
	            String uploadDirPath = System.getProperty("user.dir") + "/uploads";
	            Path uploadDir = Paths.get(uploadDirPath);
	            if (!Files.exists(uploadDir)) {
	                Files.createDirectories(uploadDir);
	            }
	            Path filePath = uploadDir.resolve(filename);
	            Files.copy(imageFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

	            // Update imagePath
	            item.setImagePath("/uploads/" + filename);
	            System.out.println("Updated image for item " + itemId + ": " + item.getImagePath());

	        } catch (IOException e) {
	            System.err.println("Failed to save updated image: " + e.getMessage());
	        }
	    }

	    // Save updated item
	    ItemMaster savedItem = itemMasterRepository.save(item);

	    // Get stock record
	    InventoryStock stock = inventoryStockRepository.findByItemId(itemId)
	        .orElseThrow(() -> new IllegalArgumentException("Stock record not found"));

	    return toDTO(savedItem, stock);
	}

	@Override
	public ItemMasterDTO getItemById(Long id) {
	    // Fetch item from DB
	    ItemMaster item = itemMasterRepository.findById(id)
	        .orElseThrow(() -> new IllegalArgumentException("Item not found with ID: " + id));

	    // Fetch stock record
	    InventoryStock stock = inventoryStockRepository.findByItemId(id)
	        .orElseThrow(() -> new IllegalArgumentException("Stock record not found for item ID: " + id));

	    // Convert to DTO
	    return toDTO(item, stock);
	}
}