package com.api.inventory.service.files;

import com.api.inventory.entity.StoredFile;
import com.api.inventory.repository.StoredFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Files inside MySQL (app.files.store=database, table stored_files): for hosting where the server's disk does not
 * last, such as a free Render web service. Photos are at most 5 MB each, so the database grows by what is uploaded.
 *
 * On start, files still in the server folders (uploads/ and private-uploads/partners/, from before the switch) are
 * copied in once, so switching never loses a photo or a document.
 */
@Component
@ConditionalOnProperty(name = "app.files.store", havingValue = "database")
public class DatabaseFileStore implements FileStore {

    private static final Logger log = LoggerFactory.getLogger(DatabaseFileStore.class);
    private static final long MAX_IMPORT_BYTES = 10L * 1024 * 1024;

    private final StoredFileRepository files;
    private final String privateDir;

    public DatabaseFileStore(StoredFileRepository files, @Value("${app.private-upload-dir:private-uploads}") String privateDir) {
        this.files = files;
        this.privateDir = privateDir;
    }

    @Override
    @Transactional
    public void put(String area, String name, byte[] bytes, String contentType) {
        FileStore.safeName(name);
        StoredFile f = files.findByAreaAndName(area, name).orElseGet(StoredFile::new);
        f.setArea(area);
        f.setName(name);
        f.setContentType(contentType == null ? FileStore.typeOf(name) : contentType);
        f.setSizeBytes(bytes.length);
        f.setData(bytes);
        f.setCreatedAt(Instant.now());
        files.save(f);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Content> get(String area, String name) {
        try {
            FileStore.safeName(name);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return files.findByAreaAndName(area, name).map(f -> new Content(f.getData(), f.getContentType()));
    }

    @Override
    @Transactional
    public void delete(String area, String name) {
        try {
            FileStore.safeName(name);
        } catch (IllegalArgumentException e) {
            return;
        }
        files.findByAreaAndName(area, name).ifPresent(files::delete);
    }

    /** Copies files left in the server folders into the database (once each; files already there are skipped). */
    @EventListener(ApplicationReadyEvent.class)
    public void importFolders() {
        for (String area : List.of(FileStore.PUBLIC, FileStore.PARTNERS)) {
            Path folder = DiskFileStore.folderOf(area, privateDir);
            if (!Files.isDirectory(folder)) {
                continue;
            }
            int copied = 0;
            try (Stream<Path> list = Files.list(folder)) {
                for (Path p : list.filter(Files::isRegularFile).toList()) {
                    String name = p.getFileName().toString();
                    try {
                        FileStore.safeName(name);
                    } catch (IllegalArgumentException e) {
                        log.warn("Not copied into the database (unusual file name): {}", name);
                        continue;
                    }
                    if (files.existsByAreaAndName(area, name) || Files.size(p) > MAX_IMPORT_BYTES) {
                        continue;
                    }
                    put(area, name, Files.readAllBytes(p), FileStore.typeOf(name));
                    copied++;
                }
            } catch (IOException e) {
                log.warn("Could not read {}: {}", folder, e.getMessage());
            }
            if (copied > 0) {
                log.info("Copied {} file(s) from {} into the database", copied, folder);
            }
        }
    }
}
