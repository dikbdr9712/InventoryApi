package com.api.inventory.service.files;

import java.util.Optional;

/**
 * Where uploaded files are kept: product photos (area "uploads", public at /uploads/{name}) and sellers' and
 * drivers' documents (area "partners", private, opened by admins only).
 *
 *   app.files.store=disk      (default) folders on the server: uploads/ and private-uploads/partners/
 *   app.files.store=database  inside MySQL (table stored_files). For hosting without a lasting disk, such as a free
 *                             Render web service, where every file on disk is lost when the service restarts.
 */
public interface FileStore {

    String PUBLIC = "uploads";
    String PARTNERS = "partners";

    record Content(byte[] bytes, String contentType) {
    }

    void put(String area, String name, byte[] bytes, String contentType);

    Optional<Content> get(String area, String name);

    void delete(String area, String name);

    /** A stored name may only be a plain file name: no folders, no "..". */
    static String safeName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,119}") || name.contains("..")) {
            throw new IllegalArgumentException("Bad file name.");
        }
        return name;
    }

    /** The content type from a file name's extension (photos and PDF). */
    static String typeOf(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }
}
