package com.api.inventory.service;

import com.api.inventory.entity.ItemMaster;
import com.api.inventory.repository.ItemMasterRepository;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Size and colour options: a product can be an option of a main product ("Gho, size M" of "Gho"). The website shows
 * one card for the main product and its options, and the choices on the product page. Each option keeps its own
 * price, stock and photo, so orders, stock and returns work as for any product.
 */
@Component
public class ProductOptions {

    private final ItemMasterRepository items;

    public ProductOptions(ItemMasterRepository items) {
        this.items = items;
    }

    /**
     * Sets what this product is an option of (empty = a product of its own) and its short option name.
     * The main product must exist, be sold by the same seller (or both ours), and not be an option itself; a product
     * that has options cannot become an option.
     */
    public void apply(ItemMaster item, Long variantOf, String variantName) {
        String name = variantName == null ? "" : variantName.trim();
        if (name.length() > 60) {
            throw new IllegalStateException("Keep the option name short (60 characters at most), for example \"Size M\".");
        }
        if (variantOf == null) {
            item.setVariantOf(null);
            item.setVariantName(name.isEmpty() ? null : name);
            return;
        }
        if (item.getItemId() != null && variantOf.equals(item.getItemId())) {
            throw new IllegalStateException("A product cannot be an option of itself.");
        }
        ItemMaster main = items.findById(variantOf)
                .orElseThrow(() -> new IllegalStateException("The main product was not found."));
        if (main.getVariantOf() != null) {
            throw new IllegalStateException("\"" + main.getItemName() + "\" is itself an option. Choose its main product instead.");
        }
        if (!Objects.equals(main.getSellerId(), item.getSellerId())) {
            throw new IllegalStateException("An option must be sold by the same shop as its main product.");
        }
        if (item.getItemId() != null && !items.findByVariantOf(item.getItemId()).isEmpty()) {
            throw new IllegalStateException("This product has options of its own, so it cannot be an option.");
        }
        if (name.isEmpty()) {
            throw new IllegalStateException("Name the option, for example \"Size M\" or \"Red\".");
        }
        item.setVariantOf(variantOf);
        item.setVariantName(name);
    }
}
