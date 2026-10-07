package com.api.inventory;

import com.api.inventory.dto.OrderRequestDTO;
import com.api.inventory.dto.PaymentRequestDTO;
import com.api.inventory.entity.*;
import com.api.inventory.repository.*;
import com.api.inventory.security.AccessControlService;
import com.api.inventory.service.*;
import com.api.inventory.service.OrderBoardService.Board;
import com.api.inventory.service.OrderBoardService.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The order board: an order from placed to delivered, who had it at each step, the team's workload, late orders,
 * and who may take, give back and give out work. In-memory database.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:orderboard;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false",
        "app.private-upload-dir=target/test-private-uploads",
        "spring.data.jpa.repositories.bootstrap-mode=lazy"
})
class OrderBoardTest {

    @MockitoBean EmailService email;

    @Autowired AccessControlService access;
    @Autowired OrderBoardService board;
    @Autowired PackageService packages;
    @Autowired OrderService orders;
    @Autowired PaymentService payments;
    @Autowired NotificationService notifications;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired ItemMasterRepository items;
    @Autowired InventoryStockRepository stock;
    @Autowired OrderRepository orderRepo;
    @Autowired OrderPackageRepository packageRepo;
    @Autowired RiderProfileRepository riderRepo;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anOrderFromPlacedToDeliveredAndWhoHadIt() {
        User boss = user("boss@board.bt", "MANAGER", "17500001");
        User pema = user("pema@board.bt", "CONTROLLER", "17500002");
        User dorji = user("dorji@board.bt", "CONTROLLER", "17500003");
        User karma = user("karma@board.bt", "USER", "17500004");
        RiderProfile scooter = rider(user("tashi@board.bt", "RIDER", "17500005"), "Scooter");
        Long soap = item("Herbal soap", "150.00", 10);

        as(karma);
        Order order = orders.createOrder(orderFor(karma, soap, 2));

        as(boss);
        Item placed = itemOf(board.board(null, null, false), order);
        assertEquals("PAYMENT_DUE", placed.stage());
        assertEquals(2, placed.itemCount());

        as(karma);
        PaymentRequestDTO transfer = new PaymentRequestDTO();
        transfer.setOrderId(order.getOrderId());
        transfer.setPaymentMethod("bank");
        transfer.setJournalNumber("BOB 112233");
        payments.createPayment(transfer);

        as(boss);
        Item toCheck = itemOf(board.board(null, null, false), order);
        assertEquals("VERIFY", toCheck.stage());
        assertEquals("BOB 112233", toCheck.journal());

        orders.confirmPayment(order.getOrderId());
        Board b = board.board(null, null, false);
        Item toPack = itemOf(b, order);
        assertEquals("PACK", toPack.stage());
        assertEquals("boss", toPack.verifiedBy(), "who checked the payment");
        assertNotNull(toPack.verifiedAt());
        assertEquals("DP DrukBazaars", toPack.sellerName());
        assertNull(toPack.packerEmail());
        assertTrue(b.counts().toPackNotStarted() >= 1);
        assertTrue(b.canAssign());
        assertTrue(b.packers().stream().anyMatch(p -> p.email().equals(pema.getEmail())), "controllers can pack");
        assertTrue(b.packers().stream().noneMatch(p -> p.email().equals(karma.getEmail())), "customers cannot");
        Long pkg = toPack.packageId();

        // Pema takes it; Dorji cannot take it from her, nor give it back for her
        as(pema);
        packages.takePacking(pkg);
        as(dorji);
        IllegalStateException taken = assertThrows(IllegalStateException.class, () -> packages.takePacking(pkg));
        assertTrue(taken.getMessage().contains("pema is already packing it"), taken.getMessage());
        assertThrows(AccessDeniedException.class, () -> packages.releasePacking(pkg));
        assertThrows(AccessDeniedException.class, () -> packages.assignPacker(pkg, dorji.getEmail()), "controllers do not give out work");

        // the manager moves it to Dorji: both are told
        as(boss);
        assertEquals("pema", itemOf(board.board(null, null, false), order).packerName());
        assertThrows(IllegalArgumentException.class, () -> packages.assignPacker(pkg, karma.getEmail()), "a customer cannot pack");
        packages.assignPacker(pkg, dorji.getEmail());
        assertTrue(kinds(dorji).contains("PACK_ASSIGNED"));
        assertTrue(kinds(pema).contains("PACK_REASSIGNED"));

        as(dorji);
        packages.markPacked(pkg);
        as(boss);
        b = board.board(null, null, false);
        Item ready = itemOf(b, order);
        assertEquals("READY", ready.stage());
        assertEquals("dorji", ready.packerName(), "who packed it stays on record");
        assertNotNull(ready.packedAt());
        assertEquals(1, b.packers().stream().filter(p -> p.email().equals(dorji.getEmail())).findFirst().orElseThrow().packed());

        // the manager gives it to a driver, then takes it back
        packages.assignRider(pkg, scooter.getId());
        b = board.board(null, null, false);
        Item coming = itemOf(b, order);
        assertEquals("ASSIGNED", coming.stage());
        assertEquals("tashi", coming.riderName());
        assertEquals("boss", coming.riderAssignedByName());
        assertEquals(1, b.riders().stream().filter(r -> r.id().equals(scooter.getId())).findFirst().orElseThrow().active());
        assertTrue(kinds(users.findByEmail("tashi@board.bt").orElseThrow()).contains("JOB_ASSIGNED"));
        packages.removeRider(pkg);
        assertEquals("READY", itemOf(board.board(null, null, false), order).stage());
        assertTrue(kinds(users.findByEmail("tashi@board.bt").orElseThrow()).contains("JOB_MOVED"));

        // Pema delivers it herself
        as(pema);
        packages.pickUp(pkg);
        as(boss);
        Item onTheWay = itemOf(board.board(null, null, false), order);
        assertEquals("ON_THE_WAY", onTheWay.stage());
        assertEquals("pema", onTheWay.courierName());
        as(pema);
        packages.deliver(pkg, null);

        as(boss);
        b = board.board(null, null, false);
        Item done = itemOf(b, order);
        assertEquals("DELIVERED", done.stage());
        assertNotNull(done.deliveredAt());
        assertTrue(b.counts().delivered() >= 1);
        assertEquals("COMPLETED", orderRepo.findById(order.getOrderId()).orElseThrow().getOrderStatus());
    }

    @Test
    void lateOrdersAndWhoCanCarryWhat() {
        User boss = user("boss2@board.bt", "MANAGER", "17500011");
        User karma = user("karma2@board.bt", "USER", "17500012");
        RiderProfile scooter = rider(user("sonam@board.bt", "RIDER", "17500013"), "Scooter");
        RiderProfile pending = rider(user("ugyen@board.bt", "RIDER", "17500014"), "Pickup truck");
        pending.setStatus(SellerProfile.PENDING);
        riderRepo.save(pending);
        Long fridge = item("Small fridge", "9000.00", 3);

        as(karma);
        Order order = orders.createOrder(orderFor(karma, fridge, 1));
        as(boss);
        orders.confirmPayment(order.getOrderId());

        // paid 5 hours ago and still not packed: late (the target is 4 hours)
        Order o = orderRepo.findById(order.getOrderId()).orElseThrow();
        o.setPaymentVerifiedAt(Instant.now().minus(Duration.ofHours(5)));
        orderRepo.save(o);
        Board b = board.board(null, null, false);
        Item late = itemOf(b, order);
        assertTrue(late.late(), "past the packing target");
        assertTrue(late.minutesInStage() >= 299);
        assertEquals(240, late.targetMinutes());
        assertTrue(b.counts().late() >= 1);
        assertEquals("o" + order.getOrderId(), b.items().stream().filter(Item::late).findFirst().map(i -> "o" + i.orderId()).orElseThrow(),
                "late work comes first");

        Long pkg = late.packageId();
        packages.markPacked(pkg);
        OrderPackage p = packageRepo.findById(pkg).orElseThrow();
        p.setDeliverySize("BULKY");
        packageRepo.save(p);
        IllegalStateException tooBig = assertThrows(IllegalStateException.class, () -> packages.assignRider(pkg, scooter.getId()));
        assertTrue(tooBig.getMessage().contains("cannot carry it"), tooBig.getMessage());
        assertThrows(IllegalStateException.class, () -> packages.assignRider(pkg, pending.getId()), "not approved yet");

        // a seller's package is packed by the seller, not taken by our staff
        p.setSellerId(999L);
        p.setStatus(OrderPackage.TO_PACK);
        packageRepo.save(p);
        IllegalStateException sellers = assertThrows(IllegalStateException.class, () -> packages.takePacking(pkg));
        assertTrue(sellers.getMessage().contains("packed by the seller"), sellers.getMessage());
    }

    @Test
    void thePeriodChoosesOrdersByPlacedDateAndNeverHidesOpenWork() {
        User boss = user("boss3@board.bt", "MANAGER", "17500021");
        User karma = user("karma3@board.bt", "USER", "17500022");
        Long tea = item("Green tea", "200.00", 20);

        as(karma);
        Order recent = orders.createOrder(orderFor(karma, tea, 1));
        Order stuck = orders.createOrder(orderFor(karma, tea, 1));       // placed 40 days ago, never paid
        Order oldDone = orders.createOrder(orderFor(karma, tea, 1));     // placed 40 days ago, cancelled
        as(boss);
        orders.cancelOrder(oldDone.getOrderId());
        for (Order o : List.of(stuck, oldDone)) { // the placed time cannot change through the app, so straight in the table
            jdbc.update("update orders set created_at = ? where order_id = ?", LocalDateTime.now().minusDays(40), o.getOrderId());
        }

        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Thimphu"));
        Board thisWeek = board.board(today.minusDays(6), today, false);
        assertEquals(today.minusDays(6), thisWeek.from());
        assertTrue(thisWeek.items().stream().anyMatch(i -> i.orderId().equals(recent.getOrderId())));
        assertTrue(thisWeek.items().stream().noneMatch(i -> i.orderId().equals(stuck.getOrderId())), "placed before the period");
        assertTrue(thisWeek.counts().olderOpen() >= 1, "but counted, so it is never forgotten");
        assertTrue(thisWeek.counts().placed() >= 1);

        Board withOlder = board.board(today.minusDays(6), today, true);
        Item old = itemOf(withOlder, stuck);
        assertTrue(old.older(), "shown, marked as older");
        assertEquals("PAYMENT_DUE", old.stage());
        assertTrue(withOlder.items().stream().noneMatch(i -> i.orderId().equals(oldDone.getOrderId())),
                "a finished older order is not open work");

        Board lastTwoMonths = board.board(today.minusDays(60), today.minusDays(30), false);
        assertTrue(lastTwoMonths.items().stream().anyMatch(i -> i.orderId().equals(oldDone.getOrderId()) && "CANCELLED".equals(i.stage())));
        assertTrue(lastTwoMonths.items().stream().noneMatch(i -> i.orderId().equals(recent.getOrderId())), "placed after the range");
        Board swapped = board.board(today, today.minusDays(6), false);
        assertTrue(swapped.from().isBefore(swapped.to()), "a range given backwards is turned round");
    }

    // ================= Helpers =================

    private Item itemOf(Board b, Order order) {
        return b.items().stream().filter(i -> i.orderId().equals(order.getOrderId())).findFirst()
                .orElseThrow(() -> new AssertionError("order #" + order.getOrderId() + " is not on the board"));
    }

    private List<String> kinds(User u) {
        return notifications.latest(u.getEmail(), 50).stream().map(NotificationService.NotificationView::type).toList();
    }

    private RiderProfile rider(User u, String vehicle) {
        RiderProfile r = new RiderProfile();
        r.setUser(u);
        r.setPhone(u.getPhone());
        r.setVehicleType(vehicle);
        r.setVehicleNumber("BP-1-" + u.getPhone().substring(4));
        r.setLicenseExpiry(LocalDate.now().plusYears(1));
        r.setTown("Thimphu");
        r.setStatus(SellerProfile.APPROVED);
        r.setCreatedAt(Instant.now());
        return riderRepo.save(r);
    }

    private OrderRequestDTO orderFor(User customer, Long itemId, int quantity) {
        OrderRequestDTO request = new OrderRequestDTO();
        request.setCustomerName(customer.getName());
        request.setCustomerEmail(customer.getEmail());
        request.setCustomerPhone(customer.getPhone());
        request.setAddress("Motithang, Thimphu");
        request.setItems(List.of(new OrderRequestDTO.Item(itemId, quantity, null)));
        return request;
    }

    private User user(String email, String role, String phone) {
        User u = new User();
        u.setName(email.substring(0, email.indexOf('@')));
        u.setEmail(email);
        u.setPhone(phone);
        u.setPassword("x");
        u.setActive(true);
        u.setRole(roles.findByName(role).orElseThrow());
        return users.save(u);
    }

    private void as(User u) {
        String role = u.getRole().getName();
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        access.permissionsOf(role).forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(u.getEmail(), null, authorities));
    }

    private Long item(String name, String price, int qty) {
        ItemMaster item = new ItemMaster();
        item.setItemName(name);
        item.setSku("OB-" + System.nanoTime());
        item.setMrp(new BigDecimal(price));
        item.setSellingPrice(new BigDecimal(price));
        item.setIsActive(true);
        item.setCreatedAt(LocalDateTime.now());
        Long id = items.save(item).getItemId();
        InventoryStock s = new InventoryStock();
        s.setItemId(id);
        s.setCurrentQuantity(qty);
        stock.save(s);
        return id;
    }
}
