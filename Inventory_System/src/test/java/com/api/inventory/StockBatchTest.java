package com.api.inventory;

import com.api.inventory.dto.TransactionRequestDTO;
import com.api.inventory.entity.*;
import com.api.inventory.repository.*;
import com.api.inventory.security.Permissions;
import com.api.inventory.service.StockService;
import com.api.inventory.service.TransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Old and new stock of the same product at different prices: batches, first-to-expire-first, real cost
 * of every sale, expired stock never sold, returns back into their batch, counts, write-offs and profit.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stockbatches;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false",
        "app.private-upload-dir=target/test-private-uploads",
        "spring.data.jpa.repositories.bootstrap-mode=lazy"
})
class StockBatchTest {

    @Autowired StockService stock;
    @Autowired TransactionService transactions;
    @Autowired StockBatchRepository batches;
    @Autowired OrderItemBatchRepository allocations;
    @Autowired InventoryStockRepository totals;
    @Autowired ItemMasterRepository items;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository orderItems;
    @Autowired TransactionRepository movements;

    LocalDate today;

    @BeforeEach
    void signIn() {
        today = stock.today();
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
    void oldAndNewStockSellFirstToExpireFirstAtTheirOwnCost() {
        Long syrup = product("Cough syrup", "100.00", "50.00");
        StockBatch a = stock.receive(syrup, 10, new BigDecimal("50"), "A-1", today.plusDays(300), "Pharma Ltd", StockBatch.PURCHASE, null);
        StockBatch b = stock.receive(syrup, 10, new BigDecimal("60"), "B-2", today.plusDays(100), "Pharma Ltd", StockBatch.PURCHASE, null);
        StockBatch c = stock.receive(syrup, 5, new BigDecimal("70"), null, null, null, StockBatch.PURCHASE, null);
        assertEquals(25, total(syrup));

        // 12 sold: the 10 that expire first (batch B, cost 60), then 2 from A (cost 50)
        OrderItem line = soldLine(syrup, 12, "100.00");
        stock.takeForLine(line);
        assertEquals(0, left(b));
        assertEquals(8, left(a));
        assertEquals(5, left(c), "undated stock goes last");
        assertEquals(13, total(syrup));
        assertMoney("58.33", orderItems.findById(line.getOrderItemId()).orElseThrow().getUnitCost()); // (600 + 100) / 12
        assertEquals(2, allocations.findByOrderItemIdOrderByIdDesc(line.getOrderItemId()).size(), "it remembers both batches");

        // returned: back into the same batches
        stock.putBack(line.getOrderItemId(), syrup, 12, null);
        assertEquals(10, left(b));
        assertEquals(10, left(a));
        assertEquals(25, total(syrup));

        // cannot sell more than there is
        assertThrows(IllegalStateException.class, () -> stock.takeForLine(soldLine(syrup, 26, "100.00")));
        assertEquals(25, total(syrup), "a refused sale takes nothing");
    }

    @Test
    void expiredStockIsNeverSoldAndIsTakenOffSale() {
        Long drops = product("Eye drops", "80.00", "30.00");
        StockBatch old = stock.receive(drops, 4, new BigDecimal("30"), "OLD", today.plusDays(10), null, StockBatch.PURCHASE, null);
        StockBatch fresh = stock.receive(drops, 6, new BigDecimal("35"), "NEW", today.plusDays(400), null, StockBatch.PURCHASE, null);
        assertThrows(IllegalStateException.class,
                () -> stock.receive(drops, 1, BigDecimal.ONE, "X", today.minusDays(1), null, StockBatch.PURCHASE, null),
                "already expired stock cannot be put on sale");

        // time passes: the old batch's date is now behind us
        StockBatch b = batches.findById(old.getId()).orElseThrow();
        b.setExpiryDate(today.minusDays(1));
        batches.save(b);

        OrderItem line = soldLine(drops, 2, "80.00");
        stock.takeForLine(line);
        assertEquals(StockBatch.EXPIRED, batches.findById(old.getId()).orElseThrow().getStatus(), "taken off sale before the sale");
        assertEquals(4, left(fresh));
        assertMoney("35.00", orderItems.findById(line.getOrderItemId()).orElseThrow().getUnitCost(), "sold from the fresh batch");
        assertEquals(4, total(drops), "10 - 4 expired - 2 sold");
        assertTrue(movements.findAll().stream().anyMatch(t -> "EXPIRED".equals(t.getTransactionType()) && t.getQuantity() == 4));
    }

    @Test
    void countsWriteOffsAndStockFromBeforeBatches() {
        Long soap = product("Neem soap", "60.00", "25.00");

        // stock that existed before batches: only the total, no batch
        InventoryStock legacy = new InventoryStock();
        legacy.setItemId(soap);
        legacy.setCurrentQuantity(8);
        totals.save(legacy);
        stock.reconcile();
        List<StockBatch> opening = batches.findByItemIdOrderByReceivedAtDesc(soap);
        assertEquals(1, opening.size());
        assertEquals(StockBatch.OPENING, opening.get(0).getSource());
        assertEquals(8, opening.get(0).getQuantityLeft());
        assertMoney("25.00", opening.get(0).getUnitCost(), "at the product's cost price");

        // a count finds 11: 3 more, as a new batch at the last cost
        stock.setCount(soap, 11, null, null, "Shelf count");
        assertEquals(11, total(soap));
        assertEquals(2, batches.findByItemIdOrderByReceivedAtDesc(soap).size());

        // a broken one is written off, with a reason
        Long openingId = opening.get(0).getId();
        assertThrows(IllegalStateException.class, () -> stock.writeOff(openingId, 1, " "), "a reason is required");
        stock.writeOff(openingId, 1, "Broken box");
        assertEquals(10, total(soap));
        assertEquals(7, batches.findById(openingId).orElseThrow().getQuantityLeft());

        // a count finds 6: 4 fewer
        stock.setCount(soap, 6, null, null, "Shelf count");
        assertEquals(6, total(soap));
    }

    @Test
    void restockingAtANewPriceMakesANewBatchAndCanChangeTheSellingPrice() {
        Long tea = product("Herbal tea", "120.00", "70.00");
        String sku = items.findById(tea).orElseThrow().getSku();

        TransactionRequestDTO delivery = new TransactionRequestDTO();
        delivery.setSku(sku);
        delivery.setQuantity(20);
        delivery.setUnitPrice(new BigDecimal("85.00"));     // costs more now
        delivery.setCustomerOrSupplier("Tea House");
        delivery.setBatchNo("T-2026-10");
        delivery.setExpiryDate(today.plusDays(365));
        delivery.setNewSellingPrice(new BigDecimal("140.00"));
        transactions.recordPurchase(delivery);

        StockBatch batch = batches.findByItemIdOrderByReceivedAtDesc(tea).get(0);
        assertEquals("T-2026-10", batch.getBatchNo());
        assertEquals(20, batch.getQuantityLeft());
        assertMoney("85.00", batch.getUnitCost());
        assertEquals(20, total(tea));
        ItemMaster after = items.findById(tea).orElseThrow();
        assertMoney("85.00", after.getCostPrice(), "the cost price follows the latest purchase");
        assertMoney("140.00", after.getSellingPrice());
    }

    @Test
    void profitUsesWhatEachSoldUnitReallyCost() {
        Long balm = product("Balm", "200.00", "80.00");
        stock.receive(balm, 5, new BigDecimal("80"), null, today.plusDays(30), null, StockBatch.PURCHASE, null);
        stock.receive(balm, 5, new BigDecimal("120"), null, today.plusDays(300), null, StockBatch.PURCHASE, null);
        stock.takeForLine(soldLine(balm, 7, "200.00")); // 5 x 80 + 2 x 120 = 640

        StockService.ProfitReport report = stock.profit(today, today);
        assertMoney("1400.00", report.revenue());
        assertMoney("640.00", report.cost());
        assertMoney("760.00", report.grossProfit());
        assertEquals(7, report.unitsSold());
    }

    // ---------- helpers ----------

    private Long product(String name, String price, String cost) {
        ItemMaster item = new ItemMaster();
        item.setItemName(name);
        item.setSku("S-" + System.nanoTime());
        item.setSellingPrice(new BigDecimal(price));
        item.setMrp(new BigDecimal(price));
        item.setCostPrice(new BigDecimal(cost));
        item.setIsActive(true);
        item.setCreatedAt(LocalDateTime.now());
        return items.save(item).getItemId();
    }

    private OrderItem soldLine(Long itemId, int quantity, String price) {
        Order order = new Order();
        order.setCustomerName("Counter");
        order.setOrderStatus("COMPLETED");
        order.setPaymentStatus("PAID");
        order.setSource("POS");
        order.setTotalAmount(new BigDecimal(price).multiply(BigDecimal.valueOf(quantity)));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        Long orderId = orders.save(order).getOrderId();
        OrderItem line = new OrderItem();
        line.setOrderId(orderId);
        line.setItemId(itemId);
        line.setQuantity(quantity);
        line.setUnitPrice(new BigDecimal(price));
        return orderItems.save(line);
    }

    private int total(Long itemId) {
        return totals.findByItemId(itemId).map(InventoryStock::getCurrentQuantity).orElse(0);
    }

    private int left(StockBatch b) {
        return batches.findById(b.getId()).orElseThrow().getQuantityLeft();
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertMoney(expected, actual, null);
    }

    private static void assertMoney(String expected, BigDecimal actual, String message) {
        assertNotNull(actual, message);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), (message == null ? "" : message + ": ") + "expected " + expected + " but was " + actual);
    }
}
