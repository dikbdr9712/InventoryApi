package com.api.inventory;

import com.api.inventory.entity.ItemMaster;
import com.api.inventory.entity.StockBatch;
import com.api.inventory.repository.ItemMasterRepository;
import com.api.inventory.security.Permissions;
import com.api.inventory.service.NotificationService;
import com.api.inventory.service.StockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * "Warn me when stock reaches": the people who restock are told once when stock goes down to the level (sold out
 * says so), not again while it stays low, and again after a restock; the Low stock list shows it.
 * In-memory database; notifications are caught.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lowstock;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false",
        "app.private-upload-dir=target/test-private-uploads",
        "spring.data.jpa.repositories.bootstrap-mode=lazy"
})
class LowStockTest {

    @MockitoBean NotificationService notify;

    @Autowired StockService stock;
    @Autowired ItemMasterRepository items;

    @BeforeEach
    void signIn() {
        var authorities = new java.util.ArrayList<SimpleGrantedAuthority>();
        Permissions.defaultsFor("ADMIN").forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("owner@stock.bt", null, authorities));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void restockersAreToldOnceWhenStockReachesTheLevel() {
        Long tea = product("Green tea", 3);
        Long salt = product("Rock salt", null); // no warning asked for
        stock.receive(tea, 5, new BigDecimal("40"), "T-1", null, "Tea Co", StockBatch.PURCHASE, null);
        stock.receive(salt, 2, new BigDecimal("10"), "S-1", null, "Salt Co", StockBatch.PURCHASE, null);

        stock.takeLoose(tea, 1); // 4 left: still above 3
        verify(notify, never()).withPermission(anyString(), any(), anyBoolean());

        stock.takeLoose(tea, 1); // 3 left: reaches the level
        ArgumentCaptor<NotificationService.Note> note = ArgumentCaptor.forClass(NotificationService.Note.class);
        verify(notify, times(1)).withPermission(eq("stock.restock"), note.capture(), eq(false));
        assertEquals("LOW_STOCK", note.getValue().type());
        assertTrue(note.getValue().title().contains("Green tea"));
        assertTrue(note.getValue().body().contains("Only 3 left"));

        stock.takeLoose(tea, 2); // already low: no second warning
        verify(notify, times(1)).withPermission(eq("stock.restock"), any(), anyBoolean());
        stock.takeLoose(salt, 2); // no level set: never a warning
        verify(notify, times(1)).withPermission(eq("stock.restock"), any(), anyBoolean());

        var low = stock.lowStock();
        assertEquals(1, low.size(), "only products with a level appear");
        assertEquals(tea, low.get(0).itemId());
        assertEquals(1, low.get(0).quantity());
        assertEquals(3, low.get(0).warnAt());

        // restocked above the level, then sold out: told again, and it says sold out
        stock.receive(tea, 9, new BigDecimal("40"), "T-2", null, "Tea Co", StockBatch.PURCHASE, null);
        assertTrue(stock.lowStock().isEmpty(), "10 is above 3");
        stock.takeLoose(tea, 10);
        verify(notify, times(2)).withPermission(eq("stock.restock"), note.capture(), eq(false));
        assertTrue(note.getValue().title().endsWith("is sold out"));
    }

    private Long product(String name, Integer warnAt) {
        ItemMaster item = new ItemMaster();
        item.setItemName(name);
        item.setSku("L-" + System.nanoTime());
        item.setSellingPrice(new BigDecimal("80"));
        item.setMrp(new BigDecimal("80"));
        item.setCostPrice(new BigDecimal("40"));
        item.setIsActive(true);
        item.setLowStockThreshold(warnAt);
        item.setCreatedAt(LocalDateTime.now());
        return items.save(item).getItemId();
    }
}
