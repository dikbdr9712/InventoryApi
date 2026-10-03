package com.api.inventory.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Product photos in the public uploads folder.
 *
 *  - Every photo gets its own name (item-12-3fa9c1d2.jpg). Before, the name came from the product name, so two
 *    products called "Phone" shared, and overwrote, one photo.
 *  - The type is read from the file's first bytes (JPG, PNG, WEBP or GIF), never trusted from the name, and the
 *    file gets the matching extension. Anything else is refused. At most 5 MB.
 *  - When a product gets a new photo, its previous photo file is deleted, but only a file this class made for
 *    the same product (old shared photos like "phone.jpg" are left alone).
 */
public final class ProductPhotos {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ProductPhotos() {
    }

    /** Throws a clear message when the file is not an acceptable photo. Call before saving anything. */
    public static void check(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return;
        }
        if (image.getSize() > MAX_BYTES) {
            throw new IllegalStateException("The photo is too big (at most 5 MB).");
        }
        if (extensionOf(image) == null) {
            throw new IllegalStateException("The photo must be a JPG, PNG, WEBP or GIF image.");
        }
    }

    /**
     * Saves the photo for this product and returns its web path ("/uploads/item-12-3fa9c1d2.jpg"),
     * or the previous path when no file was sent.
     */
    public static String save(Long itemId, MultipartFile image, String previousPath) {
        if (image == null || image.isEmpty()) {
            return previousPath;
        }
        check(image);
        String ext = extensionOf(image);
        byte[] random = new byte[4];
        RANDOM.nextBytes(random);
        String name = "item-" + itemId + "-" + HexFormat.of().formatHex(random) + "." + ext;
        try (InputStream in = image.getInputStream()) {
            Path dir = folder();
            Files.createDirectories(dir);
            Files.copy(in, dir.resolve(name));
        } catch (IOException e) {
            throw new IllegalStateException("The photo could not be saved. Please try again.");
        }
        deleteOwn(itemId, previousPath);
        return "/uploads/" + name;
    }

    private static void deleteOwn(Long itemId, String previousPath) {
        if (previousPath == null || !previousPath.startsWith("/uploads/item-" + itemId + "-")) {
            return;
        }
        String file = previousPath.substring("/uploads/".length());
        if (file.contains("/") || file.contains("\\") || file.contains("..")) {
            return;
        }
        try {
            Files.deleteIfExists(folder().resolve(file));
        } catch (IOException ignored) {
            // an old file left behind does no harm
        }
    }

    private static Path folder() {
        return Paths.get(System.getProperty("user.dir"), "uploads");
    }

    /** "jpg", "png", "webp" or "gif" from the file's first bytes; null for anything else. */
    static String extensionOf(MultipartFile file) {
        byte[] head = new byte[12];
        int n;
        try (InputStream in = file.getInputStream()) {
            n = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            return null;
        }
        if (n >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (n >= 8 && Arrays.equals(Arrays.copyOf(head, 8), new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
            return "png";
        }
        if (n >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "webp";
        }
        if (n >= 4 && head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8') {
            return "gif";
        }
        return null;
    }
}
