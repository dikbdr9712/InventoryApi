package com.api.inventory.controller;

import com.api.inventory.entity.WishlistItem;
import com.api.inventory.repository.ItemMasterRepository;
import com.api.inventory.repository.WishlistItemRepository;
import com.api.inventory.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * The signed-in person's wishlist: products saved for later with the heart. The website shows the products from
 * the product list, so this only keeps which ones and when.
 */
@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    static final int MAX_ITEMS = 200;

    private final WishlistItemRepository wishlist;
    private final ItemMasterRepository items;

    public WishlistController(WishlistItemRepository wishlist, ItemMasterRepository items) {
        this.wishlist = wishlist;
        this.items = items;
    }

    public record Saved(Long itemId, Instant addedAt) {
        static Saved of(WishlistItem w) {
            return new Saved(w.getItemId(), w.getCreatedAt());
        }
    }

    /** Newest first. */
    @GetMapping
    public List<Saved> mine() {
        return wishlist.findByUserEmailIgnoreCaseOrderByCreatedAtDesc(CurrentUser.email()).stream().map(Saved::of).toList();
    }

    /** Saving a product twice keeps the first one. */
    @PostMapping("/{itemId}")
    public Saved add(@PathVariable Long itemId) {
        String me = CurrentUser.email();
        if (!items.existsById(itemId)) {
            throw new IllegalStateException("This product does not exist any more.");
        }
        return wishlist.findByUserEmailIgnoreCaseAndItemId(me, itemId).map(Saved::of).orElseGet(() -> {
            if (wishlist.countByUserEmailIgnoreCase(me) >= MAX_ITEMS) {
                throw new IllegalStateException("Your wishlist is full (" + MAX_ITEMS + " products). Remove some first.");
            }
            WishlistItem w = new WishlistItem();
            w.setUserEmail(me);
            w.setItemId(itemId);
            w.setCreatedAt(Instant.now());
            try {
                return Saved.of(wishlist.save(w));
            } catch (DataIntegrityViolationException twice) { // two taps at once: the other one saved it
                return wishlist.findByUserEmailIgnoreCaseAndItemId(me, itemId).map(Saved::of).orElseThrow(() -> twice);
            }
        });
    }

    @DeleteMapping("/{itemId}")
    public void remove(@PathVariable Long itemId) {
        wishlist.findByUserEmailIgnoreCaseAndItemId(CurrentUser.email(), itemId).ifPresent(wishlist::delete);
    }
}
