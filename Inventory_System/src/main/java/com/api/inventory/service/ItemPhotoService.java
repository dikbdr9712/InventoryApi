package com.api.inventory.service;

import com.api.inventory.entity.ItemMaster;
import com.api.inventory.entity.ItemPhoto;
import com.api.inventory.repository.ItemMasterRepository;
import com.api.inventory.repository.ItemPhotoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

/**
 * More photos of a product, besides its main photo: at most 4 more (5 in all). Any of them can become the main photo
 * (the old main photo takes its place). Who may change them is checked by the caller (staff, or the product's seller).
 */
@Service
public class ItemPhotoService {

    public static final int MAX_EXTRA = 4;

    private final ItemPhotoRepository photos;
    private final ItemMasterRepository items;
    private final ProductPhotos files;

    public ItemPhotoService(ItemPhotoRepository photos, ItemMasterRepository items, ProductPhotos files) {
        this.photos = photos;
        this.items = items;
        this.files = files;
    }

    public record PhotoView(Long id, String path) {
    }

    public List<PhotoView> list(Long itemId) {
        return photos.findByItemIdOrderByPositionAscIdAsc(itemId).stream().map(p -> new PhotoView(p.getId(), p.getPath())).toList();
    }

    /** A product without a main photo gets this one as its main photo. */
    @Transactional
    public List<PhotoView> add(Long itemId, MultipartFile file) {
        ItemMaster item = items.findById(itemId).orElseThrow(() -> new IllegalStateException("Product not found."));
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a photo.");
        }
        ProductPhotos.check(file);
        if (item.getImagePath() == null || item.getImagePath().isBlank()) {
            item.setImagePath(files.save(itemId, file, null));
            items.save(item);
            return list(itemId);
        }
        long count = photos.countByItemId(itemId);
        if (count >= MAX_EXTRA) {
            throw new IllegalStateException("A product can have " + (MAX_EXTRA + 1) + " photos. Remove one first.");
        }
        ItemPhoto p = new ItemPhoto();
        p.setItemId(itemId);
        p.setPath(files.save(itemId, file, null));
        p.setPosition((int) count + 1);
        p.setCreatedAt(Instant.now());
        photos.save(p);
        return list(itemId);
    }

    @Transactional
    public List<PhotoView> remove(Long itemId, Long photoId) {
        ItemPhoto p = photos.findByIdAndItemId(photoId, itemId).orElseThrow(() -> new IllegalStateException("Photo not found."));
        photos.delete(p);
        files.remove(itemId, p.getPath());
        return list(itemId);
    }

    /** The chosen photo becomes the main one; the main one takes its place among the others. */
    @Transactional
    public List<PhotoView> makeMain(Long itemId, Long photoId) {
        ItemMaster item = items.findById(itemId).orElseThrow(() -> new IllegalStateException("Product not found."));
        ItemPhoto p = photos.findByIdAndItemId(photoId, itemId).orElseThrow(() -> new IllegalStateException("Photo not found."));
        String main = item.getImagePath();
        item.setImagePath(p.getPath());
        items.save(item);
        if (main == null || main.isBlank()) {
            photos.delete(p);
        } else {
            p.setPath(main);
            photos.save(p);
        }
        return list(itemId);
    }
}
