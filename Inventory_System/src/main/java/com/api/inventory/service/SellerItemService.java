package com.api.inventory.service;

import com.api.inventory.dto.ItemMasterDTO;
import com.api.inventory.entity.InventoryStock;
import com.api.inventory.entity.ItemMaster;
import com.api.inventory.entity.SellerProfile;
import com.api.inventory.entity.Transaction;
import com.api.inventory.repository.InventoryStockRepository;
import com.api.inventory.repository.ItemMasterRepository;
import com.api.inventory.repository.TransactionRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * A seller's own products. A seller can only see and change products that belong to them.
 * They set the name, price and stock; the cost price and tax fields of the shop's own catalogue are not used.
 */
@Service
public class SellerItemService {


    private final ItemMasterRepository items;
    private final InventoryStockRepository stock;
    private final TransactionRepository transactions;

    private StockService stockService;

    @org.springframework.beans.factory.annotation.Autowired
    void setStockService(StockService stockService) {
        this.stockService = stockService;
    }

    private ProductPhotos photos;

    @org.springframework.beans.factory.annotation.Autowired
    void setPhotos(ProductPhotos photos) {
        this.photos = photos;
    }

    private ProductOptions options;

    @org.springframework.beans.factory.annotation.Autowired
    void setOptions(ProductOptions options) {
        this.options = options;
    }

    /** The seller's own product, or AccessDeniedException (for photos and other seller actions). */
    public ItemMaster ownProduct(SellerProfile seller, Long itemId) {
        return own(seller, itemId);
    }

    public SellerItemService(ItemMasterRepository items, InventoryStockRepository stock, TransactionRepository transactions) {
        this.items = items;
        this.stock = stock;
        this.transactions = transactions;
    }

    /** What a seller fills in. Sent as a multipart form so a photo can come with it. */
    public static class SellerItemForm {
        private String itemName;
        private String category;
        private String description;
        private String uom;
        private BigDecimal sellingPrice;
        private BigDecimal mrp;
        private Integer quantity;
        private Boolean isActive;
        private String deliverySize;
        private Long variantOf;      // a size/colour of this main product (one of the seller's own)
        private String variantName;  // "Size M", "Red"

        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getUom() { return uom; }
        public void setUom(String uom) { this.uom = uom; }
        public BigDecimal getSellingPrice() { return sellingPrice; }
        public void setSellingPrice(BigDecimal sellingPrice) { this.sellingPrice = sellingPrice; }
        public BigDecimal getMrp() { return mrp; }
        public void setMrp(BigDecimal mrp) { this.mrp = mrp; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }
        public String getDeliverySize() { return deliverySize; }
        public void setDeliverySize(String deliverySize) { this.deliverySize = deliverySize; }
        public Long getVariantOf() { return variantOf; }
        public void setVariantOf(Long variantOf) { this.variantOf = variantOf; }
        public String getVariantName() { return variantName; }
        public void setVariantName(String variantName) { this.variantName = variantName; }
    }

    public List<ItemMasterDTO> list(SellerProfile seller) {
        return items.findBySellerIdOrderByItemIdDesc(seller.getId()).stream().map(item -> toDto(item, seller)).toList();
    }

    @Transactional
    public ItemMasterDTO create(SellerProfile seller, SellerItemForm form, MultipartFile image) {
        ItemMaster item = new ItemMaster();
        apply(item, form);
        item.setSellerId(seller.getId());
        options.apply(item, form.getVariantOf(), form.getVariantName());
        item.setCostPrice(BigDecimal.ZERO);
        item.setTaxRate(BigDecimal.ZERO);
        item.setDiscountAllowed(false);
        item.setIsActive(true);
        item.setCreatedAt(LocalDateTime.now());
        item.setSku("TEMP-" + System.nanoTime());
        ItemMaster saved = items.save(item);
        saved.setSku("S" + seller.getId() + "-ITEM-" + saved.getItemId());
        saveImage(saved, image);
        items.save(saved);

        InventoryStock s = new InventoryStock();
        s.setItemId(saved.getItemId());
        s.setCurrentQuantity(0);
        s.setLastUpdated(LocalDateTime.now());
        stock.save(s);
        setStock(saved, form.getQuantity() == null ? 0 : form.getQuantity(), seller);

        return toDto(saved, seller);
    }

    @Transactional
    public ItemMasterDTO update(SellerProfile seller, Long itemId, SellerItemForm form, MultipartFile image) {
        ItemMaster item = own(seller, itemId);
        apply(item, form);
        options.apply(item, form.getVariantOf(), form.getVariantName());
        if (form.getIsActive() != null) {
            item.setIsActive(form.getIsActive());
        }
        saveImage(item, image);
        items.save(item);
        if (form.getQuantity() != null) {
            setStock(item, form.getQuantity(), seller);
        }
        return toDto(item, seller);
    }

    private ItemMaster own(SellerProfile seller, Long itemId) {
        ItemMaster item = items.findById(itemId).orElseThrow(() -> new IllegalStateException("Product not found."));
        if (!seller.getId().equals(item.getSellerId())) {
            throw new AccessDeniedException("This is not your product.");
        }
        return item;
    }

    private static void apply(ItemMaster item, SellerItemForm form) {
        String name = form.getItemName() == null ? "" : form.getItemName().trim();
        if (name.isEmpty() || name.length() > 120) {
            throw new IllegalStateException("Enter the product name (at most 120 characters).");
        }
        BigDecimal price = form.getSellingPrice();
        if (price == null || price.signum() <= 0) {
            throw new IllegalStateException("Enter a selling price above zero.");
        }
        price = price.setScale(2, RoundingMode.HALF_UP);
        BigDecimal mrp = form.getMrp() == null || form.getMrp().signum() <= 0 ? price : form.getMrp().setScale(2, RoundingMode.HALF_UP);
        if (mrp.compareTo(price) < 0) {
            throw new IllegalStateException("The MRP cannot be lower than the selling price.");
        }
        String category = form.getCategory() == null || form.getCategory().isBlank() ? "Uncategorized" : form.getCategory().trim();

        item.setItemName(name);
        item.setCategory(category.substring(0, Math.min(100, category.length())));
        item.setDescription(form.getDescription() == null ? null : form.getDescription().trim());
        item.setUom(form.getUom() == null || form.getUom().isBlank() ? "pcs" : form.getUom().trim());
        item.setSellingPrice(price);
        item.setMrp(mrp);
        if (form.getDeliverySize() != null || item.getDeliverySize() == null) {
            item.setDeliverySize(com.api.inventory.entity.DeliverySize.parse(form.getDeliverySize()));
        }
    }

    /** Sets the stock to an exact number and writes the change into the stock ledger. */
    private void setStock(ItemMaster item, int quantity, SellerProfile seller) {
        if (quantity < 0 || quantity > 100_000) {
            throw new IllegalStateException("Stock must be between 0 and 100000.");
        }
        int current = stock.findByItemId(item.getItemId()).map(InventoryStock::getCurrentQuantity).orElse(0);
        int delta = quantity - current;
        if (delta == 0) {
            return;
        }
        stockService.setCount(item.getItemId(), quantity, BigDecimal.ZERO, com.api.inventory.entity.StockBatch.SELLER,
                "Seller " + seller.getShopName() + " set the stock");

        Transaction tx = new Transaction();
        tx.setItemId(item.getItemId());
        tx.setTransactionType(delta > 0 ? "PURCHASE" : "ADJUSTMENT");
        tx.setQuantity(Math.abs(delta));
        tx.setUnitPrice(BigDecimal.ZERO);
        tx.setCustomerOrSupplier(seller.getShopName());
        tx.setNotes("Stock set by seller to " + quantity);
        tx.setReferenceType("SELLER_STOCK");
        tx.setReferenceId(seller.getId());
        tx.setCreatedAt(LocalDateTime.now());
        transactions.save(tx);
    }

    /** Each seller photo gets its own file name and its real type is checked (see ProductPhotos). */
    private void saveImage(ItemMaster item, MultipartFile image) {
        item.setImagePath(photos.save(item.getItemId(), image, item.getImagePath()));
    }

    private ItemMasterDTO toDto(ItemMaster item, SellerProfile seller) {
        ItemMasterDTO dto = new ItemMasterDTO();
        dto.setItemId(item.getItemId());
        dto.setSku(item.getSku());
        dto.setItemName(item.getItemName());
        dto.setCategory(item.getCategory());
        dto.setDescription(item.getDescription());
        dto.setUom(item.getUom());
        dto.setSellingPrice(item.getSellingPrice());
        dto.setMrp(item.getMrp());
        dto.setImagePath(item.getImagePath());
        dto.setIsActive(item.getIsActive());
        dto.setCreatedAt(item.getCreatedAt());
        dto.setVariantOf(item.getVariantOf());
        dto.setVariantName(item.getVariantName());
        dto.setSellerId(seller.getId());
        dto.setDeliverySize(com.api.inventory.entity.DeliverySize.of(item.getDeliverySize()).name());
        dto.setSellerName(seller.getShopName());
        int qty = stock.findByItemId(item.getItemId()).map(InventoryStock::getCurrentQuantity).orElse(0);
        dto.setCurrentQuantity(qty);
        dto.setStatus(qty > 0 ? "Available" : "Unavailable");
        dto.setAvailability(dto.getStatus());
        return dto;
    }
}
