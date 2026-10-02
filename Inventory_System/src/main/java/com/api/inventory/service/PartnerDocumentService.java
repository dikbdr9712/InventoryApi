package com.api.inventory.service;

import com.api.inventory.entity.PartnerDocument;
import com.api.inventory.repository.PartnerDocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Identity and business documents of sellers and drivers (CID, driving licence, trade licence).
 *
 *  - Stored in the PRIVATE folder (app.private-upload-dir), never in the public uploads folder.
 *  - Saved under a random name; the person's own file name is only remembered for the admin.
 *  - Only JPG, PNG or PDF, checked by the file's first bytes (not just its name), at most 5 MB.
 *  - Opened only through an admin-only endpoint.
 */
@Service
public class PartnerDocumentService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final PartnerDocumentRepository docs;
    private final Path folder;

    public PartnerDocumentService(PartnerDocumentRepository docs, @Value("${app.private-upload-dir:private-uploads}") String dir) {
        this.docs = docs;
        this.folder = Paths.get(dir).toAbsolutePath().resolve("partners");
    }

    public record DocumentView(Long id, String kind, String originalName, String contentType, Long sizeBytes, Instant uploadedAt) {
    }

    public List<DocumentView> list(String partnerType, Long partnerId) {
        return docs.findByPartnerTypeAndPartnerIdAndCurrentTrueOrderByIdDesc(partnerType, partnerId).stream()
                .map(d -> new DocumentView(d.getId(), d.getKind(), d.getOriginalName(), d.getContentType(), d.getSizeBytes(), d.getUploadedAt()))
                .toList();
    }

    public boolean has(String partnerType, Long partnerId, String kind) {
        return !docs.findByPartnerTypeAndPartnerIdAndKindAndCurrentTrue(partnerType, partnerId, kind).isEmpty();
    }

    /** Checks a file BEFORE anything is saved, so a bad file never leaves half an application behind. */
    public static void check(MultipartFile file, String label) {
        if (file == null || file.isEmpty()) {
            return;
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalStateException(label + " is too big (at most 5 MB). Take a smaller photo or scan.");
        }
        if (typeOf(file) == null) {
            throw new IllegalStateException(label + " must be a photo (JPG or PNG) or a PDF.");
        }
    }

    /** Saves a new document of this kind; an older one of the same kind is kept but no longer shown. */
    public PartnerDocument store(String partnerType, Long partnerId, String kind, MultipartFile file) {
        String[] type = typeOf(file);
        if (type == null) {
            throw new IllegalStateException("Only JPG, PNG or PDF files can be sent.");
        }
        String stored = UUID.randomUUID() + "." + type[1];
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(folder);
            Files.copy(in, folder.resolve(stored), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("The document could not be saved. Please try again.");
        }
        for (PartnerDocument old : docs.findByPartnerTypeAndPartnerIdAndKindAndCurrentTrue(partnerType, partnerId, kind)) {
            old.setCurrent(false);
            docs.save(old);
        }
        PartnerDocument d = new PartnerDocument();
        d.setPartnerType(partnerType);
        d.setPartnerId(partnerId);
        d.setKind(kind);
        d.setStoredName(stored);
        String original = file.getOriginalFilename() == null ? kind : file.getOriginalFilename().replaceAll("[\\\\/:*?\"<>|]", "_");
        d.setOriginalName(original.substring(0, Math.min(200, original.length())));
        d.setContentType(type[0]);
        d.setSizeBytes(file.getSize());
        d.setUploadedAt(Instant.now());
        d.setCurrent(true);
        return docs.save(d);
    }

    public PartnerDocument find(Long id) {
        return docs.findById(id).orElseThrow(() -> new IllegalStateException("Document not found."));
    }

    public Resource file(PartnerDocument d) {
        Path path = folder.resolve(d.getStoredName()).normalize();
        if (!path.startsWith(folder) || !Files.exists(path)) {
            throw new IllegalStateException("The document file is missing.");
        }
        return new FileSystemResource(path);
    }

    /** [content type, extension] from the file's first bytes, or null when it is not JPG/PNG/PDF. */
    private static String[] typeOf(MultipartFile file) {
        byte[] head = new byte[8];
        int n;
        try (InputStream in = file.getInputStream()) {
            n = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            return null;
        }
        if (n >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return new String[] {"image/jpeg", "jpg"};
        }
        if (n >= 8 && Arrays.equals(Arrays.copyOf(head, 8), new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
            return new String[] {"image/png", "png"};
        }
        if (n >= 4 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F') {
            return new String[] {"application/pdf", "pdf"};
        }
        return null;
    }
}
