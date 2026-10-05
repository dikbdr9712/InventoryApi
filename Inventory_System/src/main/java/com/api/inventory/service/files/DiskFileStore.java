package com.api.inventory.service.files;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/** Files in folders on the server (app.files.store=disk, the default): uploads/ and private-uploads/partners/. */
@Component
@ConditionalOnProperty(name = "app.files.store", havingValue = "disk", matchIfMissing = true)
public class DiskFileStore implements FileStore {

    private final Path publicFolder;
    private final Path partnersFolder;

    public DiskFileStore(@Value("${app.private-upload-dir:private-uploads}") String privateDir) {
        this.publicFolder = Paths.get(System.getProperty("user.dir"), "uploads").toAbsolutePath();
        this.partnersFolder = Paths.get(privateDir).toAbsolutePath().resolve("partners");
    }

    /** The folder of an area (also used to copy old files into the database, see DatabaseFileStore). */
    public static Path folderOf(String area, String privateDir) {
        return FileStore.PARTNERS.equals(area)
                ? Paths.get(privateDir).toAbsolutePath().resolve("partners")
                : Paths.get(System.getProperty("user.dir"), "uploads").toAbsolutePath();
    }

    private Path pathOf(String area, String name) {
        Path folder = FileStore.PARTNERS.equals(area) ? partnersFolder : publicFolder;
        Path path = folder.resolve(FileStore.safeName(name)).normalize();
        if (!path.startsWith(folder)) {
            throw new IllegalArgumentException("Bad file name.");
        }
        return path;
    }

    @Override
    public void put(String area, String name, byte[] bytes, String contentType) {
        try {
            Path path = pathOf(area, name);
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException e) {
            throw new IllegalStateException("The file could not be saved. Please try again.");
        }
    }

    @Override
    public Optional<Content> get(String area, String name) {
        try {
            Path path = pathOf(area, name);
            return Files.isRegularFile(path) ? Optional.of(new Content(Files.readAllBytes(path), FileStore.typeOf(name))) : Optional.empty();
        } catch (IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String area, String name) {
        try {
            Files.deleteIfExists(pathOf(area, name));
        } catch (IOException | IllegalArgumentException ignored) {
            // an old file left behind does no harm
        }
    }
}
