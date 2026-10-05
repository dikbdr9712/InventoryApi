package com.api.inventory.controller;

import com.api.inventory.service.files.FileStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Product photos at /uploads/{name} when they are kept in the database (app.files.store=database). With the default
 * disk store, StaticResourceConfig serves the uploads folder instead. Public, like any shop picture.
 * A photo never changes under its name (a new photo gets a new name), so browsers may keep it for a month.
 */
@RestController
@ConditionalOnProperty(name = "app.files.store", havingValue = "database")
public class UploadsController {

    private final FileStore store;

    public UploadsController(FileStore store) {
        this.store = store;
    }

    @GetMapping("/uploads/{name}")
    public ResponseEntity<byte[]> photo(@PathVariable String name) {
        return store.get(FileStore.PUBLIC, name)
                .map(c -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(c.contentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                        .body(c.bytes()))
                .orElse(ResponseEntity.notFound().build());
    }
}
