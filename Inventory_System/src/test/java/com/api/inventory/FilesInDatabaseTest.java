package com.api.inventory;

import com.api.inventory.entity.PartnerDocument;
import com.api.inventory.repository.StoredFileRepository;
import com.api.inventory.service.EmailService;
import com.api.inventory.service.PartnerDocumentService;
import com.api.inventory.service.ProductPhotos;
import com.api.inventory.service.files.DatabaseFileStore;
import com.api.inventory.service.files.FileStore;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Hosting without a lasting disk (app.files.store=database): product photos and partner documents are kept in the
 * database, photos are served at /uploads/{name}, documents never are. In-memory database.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:filesdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false",
        "app.files.store=database",
        "app.private-upload-dir=target/test-private-uploads-db",
        "spring.data.jpa.repositories.bootstrap-mode=lazy"
})
class FilesInDatabaseTest {

    private static final byte[] PNG_HEAD = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @MockitoBean EmailService email;

    @Autowired WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") Filter securityChain;
    @Autowired FileStore store;
    @Autowired ProductPhotos photos;
    @Autowired PartnerDocumentService documents;
    @Autowired StoredFileRepository storedFiles;

    @Test
    void photosAndDocumentsLiveInTheDatabase() throws Exception {
        assertInstanceOf(DatabaseFileStore.class, store, "app.files.store=database picks the database store");
        MockMvc http = MockMvcBuilders.webAppContextSetup(context).addFilters(securityChain).build();

        byte[] first = png(100);
        String path = photos.save(42L, new MockMultipartFile("image", "a.png", "image/png", first), null);
        assertTrue(path.matches("/uploads/item-42-[0-9a-f]{8}\\.png"), path);
        String name = path.substring("/uploads/".length());
        assertTrue(storedFiles.existsByAreaAndName(FileStore.PUBLIC, name));

        // served to anyone (no sign-in), with its type and a long cache
        http.perform(get(path)).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(first))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=2592000")));

        // a new photo replaces the product's old one
        String second = photos.save(42L, new MockMultipartFile("image", "b.png", "image/png", png(200)), path);
        assertNotEquals(path, second);
        assertFalse(storedFiles.existsByAreaAndName(FileStore.PUBLIC, name), "the old photo is removed");
        http.perform(get(path)).andExpect(status().isNotFound());
        http.perform(get("/uploads/..%2Fsecrets.properties")).andExpect(status().is4xxClientError());

        // an identity document: kept, readable by the service, but never at /uploads
        byte[] pdf = "%PDF-1.4 test".getBytes();
        PartnerDocument doc = documents.store("RIDER", 7L, "LICENCE", new MockMultipartFile("file", "licence.pdf", "application/pdf", pdf));
        assertArrayEquals(pdf, documents.file(doc).getContentAsByteArray());
        assertTrue(storedFiles.existsByAreaAndName(FileStore.PARTNERS, doc.getStoredName()));
        http.perform(get("/uploads/" + doc.getStoredName())).andExpect(status().isNotFound());
    }

    private static byte[] png(int size) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(PNG_HEAD);
        for (int i = 0; i < size; i++) {
            out.write(i);
        }
        return out.toByteArray();
    }
}
