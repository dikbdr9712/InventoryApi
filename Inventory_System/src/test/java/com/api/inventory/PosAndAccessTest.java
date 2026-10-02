package com.api.inventory;

import com.api.inventory.controller.AdminController;
import com.api.inventory.dto.PosSaleRequestDTO;
import com.api.inventory.dto.TaxInfoDTO;
import com.api.inventory.entity.*;
import com.api.inventory.repository.*;
import com.api.inventory.security.AccessControlService;
import com.api.inventory.service.OrderService;
import com.api.inventory.service.PosShiftService;
import com.api.inventory.service.PosShiftService.CloseRequest;
import com.api.inventory.service.PosShiftService.OpenRequest;
import com.api.inventory.service.PosShiftService.ShiftReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Roles and permissions stored in the database, and the till: shifts, discounts, duplicate sales, cash counts.
 * In-memory database only. Every person "signs in" with the permissions their role has in the database.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:posaccess;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.private-upload-dir=target/test-private-uploads",
        "spring.data.jpa.repositories.bootstrap-mode=lazy"
})
class PosAndAccessTest {

    @Autowired AdminController admin;
    @Autowired AccessControlService access;
    @Autowired PosShiftService shifts;
    @Autowired OrderService orders;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired ItemMasterRepository items;
    @Autowired InventoryStockRepository stock;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rolesAndPermissionsAreManagedSafely() {
        User boss = user("boss@test.bt", "ADMIN", "17100001");
        User manager = user("manager@test.bt", "MANAGER", "17100002");

        // the built-in roles exist with their defaults; ADMIN always has everything
        assertTrue(access.roleHas("MANAGER", "pos.discount"));
        assertFalse(access.roleHas("MANAGER", "users.manage"));
        assertTrue(access.roleHas("ADMIN", "marketplace.manage"));
        assertFalse(access.roleHas("ADMIN", "seller.portal"), "an admin has no shop of their own");
        assertFalse(access.roleHas("ADMIN", "rider.jobs"), "an admin is not a driver");
        // sellers and drivers get their detailed rights, and nothing from the shop
        assertTrue(access.roleHas("SELLER", "seller.products") && access.roleHas("SELLER", "seller.orders")
                && access.roleHas("SELLER", "seller.earnings"));
        assertTrue(access.roleHas("RIDER", "rider.jobs") && access.roleHas("RIDER", "rider.earnings"));
        assertFalse(access.roleHas("SELLER", "pos.use"));
        assertFalse(access.roleHas("RIDER", "orders.view"));

        // an admin creates a Cashier role with tick boxes
        as(boss);
        AdminController.RoleView cashier = admin.createRole(new AdminController.RoleRequest("Cashier", "Front counter", List.of("pos.use")));
        assertEquals("CASHIER", cashier.name());
        assertThrows(IllegalStateException.class, () -> admin.createRole(new AdminController.RoleRequest("cashier", null, List.of())), "duplicate name");
        assertThrows(IllegalStateException.class, () -> admin.createRole(new AdminController.RoleRequest("Spy", null, List.of("made.up"))), "unknown permission");

        // the Admin role cannot be edited, and nobody edits their own role
        Long adminRoleId = roles.findByName("ADMIN").orElseThrow().getId();
        assertThrows(IllegalStateException.class, () -> admin.updateRole(adminRoleId, new AdminController.RoleRequest(null, null, List.of())));

        // a hired cashier, made by the admin
        AdminController.UserView sam = admin.createUser(new AdminController.NewUserRequest("Sam", "sam@test.bt", "17100003", "secret1", cashier.id()));
        assertEquals("CASHIER", sam.roleName());

        // a non-admin with users.manage cannot hand out user management, or make an admin
        Role mgr = roles.findByName("MANAGER").orElseThrow();
        mgr.getPermissions().add("users.manage");
        roles.save(mgr);
        access.forgetAll();
        as(users.findByEmail(manager.getEmail()).orElseThrow());
        assertThrows(AccessDeniedException.class,
                () -> admin.updateRole(cashier.id(), new AdminController.RoleRequest(null, null, List.of("pos.use", "users.manage"))));
        assertThrows(AccessDeniedException.class, () -> admin.updateUserRole(sam.id(), adminRoleId));
        assertThrows(AccessDeniedException.class, () -> admin.updateRole(mgr.getId(), new AdminController.RoleRequest(null, null, List.of())),
                "cannot edit own role");
        // but may give the cashier the right to discount
        admin.updateRole(cashier.id(), new AdminController.RoleRequest(null, null, List.of("pos.use", "pos.discount")));
        assertTrue(access.roleHas("CASHIER", "pos.discount"), "the change works at once");

        // sellers and riders only come from Marketplace
        Long sellerRole = roles.findByName("SELLER").orElseThrow().getId();
        as(boss);
        assertThrows(IllegalStateException.class, () -> admin.updateUserRole(sam.id(), sellerRole));

        // the last admin cannot be switched off or demoted; nobody changes themselves
        assertThrows(AccessDeniedException.class, () -> admin.setActive(boss.getId(), new AdminController.ActiveRequest(false)));
        User boss2 = user("boss2@test.bt", "ADMIN", "17100009");
        as(boss2);
        assertThrows(IllegalStateException.class, () -> {
            boss.setActive(true);
            users.save(boss);
            admin.setActive(boss.getId(), new AdminController.ActiveRequest(false)); // fine: boss2 stays
            as(boss);                                                                // (boss is now off)
            admin.setActive(boss2.getId(), new AdminController.ActiveRequest(false)); // not fine: last one
        });

        // a role in use cannot be deleted; the log records what happened
        as(boss2);
        assertThrows(IllegalStateException.class, () -> admin.deleteRole(cashier.id()));
        assertTrue(admin.auditLog(50).stream().anyMatch(a -> a.action().equals("ROLE_PERMISSIONS_CHANGED")));
        assertTrue(admin.auditLog(50).stream().anyMatch(a -> a.action().equals("USER_DEACTIVATED")));
    }

    @Test
    void theTillFromOpeningToCountingTheDrawer() {
        User cashierUser = user("till@test.bt", "CONTROLLER", "17200001");   // pos.use, no extra discounts
        User managerUser = user("mgr@test.bt", "MANAGER", "17200002");       // pos.use + pos.discount + shifts
        Long tea = item("Green tea", "200.00", "180.00", 10, "20");          // shop price is 10% off MRP; max 20%

        as(cashierUser);
        // no open drawer: no sale
        assertThrows(IllegalStateException.class, () -> orders.createInPersonSale(sale("ref-0", "CASH", tea, 1, "10", null)));

        ShiftReport opened = shifts.open(new OpenRequest(new BigDecimal("500")));
        assertThrows(IllegalStateException.class, () -> shifts.open(new OpenRequest(BigDecimal.ZERO)), "one open shift each");

        // the shop discount (10%) is fine; 15% needs "Give extra discounts"
        Order first = orders.createInPersonSale(sale("ref-1", "CASH", tea, 2, "10", "400"));
        assertMoney("360.00", first.getTotalAmount()); // 2 x 180, no tax
        assertEquals("till@test.bt", first.getCashier());
        assertEquals(opened.id(), first.getShiftId());
        IllegalStateException noRight = assertThrows(IllegalStateException.class,
                () -> orders.createInPersonSale(sale("ref-2", "CASH", tea, 1, "15", null)));
        assertTrue(noRight.getMessage().contains("not allowed"), noRight.getMessage());

        // pressing Complete twice saves ONE sale and takes stock once
        Order again = orders.createInPersonSale(sale("ref-1", "CASH", tea, 2, "10", "400"));
        assertEquals(first.getOrderId(), again.getOrderId());
        assertEquals(8, stock.findByItemId(tea).orElseThrow().getCurrentQuantity());

        // too little cash, unknown tax and a silly tax rate are refused
        assertThrows(IllegalStateException.class, () -> orders.createInPersonSale(sale("ref-3", "CASH", tea, 1, "10", "100")));
        PosSaleRequestDTO badTax = sale("ref-4", "CARD", tea, 1, "10", null);
        TaxInfoDTO t = new TaxInfoDTO();
        t.setType("GST");
        t.setRate(80.0);
        badTax.setTaxes(List.of(t));
        assertThrows(IllegalArgumentException.class, () -> orders.createInPersonSale(badTax));

        // a card sale with 5% GST: 180 + 9 = 189
        PosSaleRequestDTO card = sale("ref-5", "CARD", tea, 1, "10", null);
        TaxInfoDTO gst = new TaxInfoDTO();
        gst.setType("GST");
        gst.setRate(5.0);
        card.setTaxes(List.of(gst));
        assertMoney("189.00", orders.createInPersonSale(card).getTotalAmount());

        // the cashier cannot sell more than is left
        assertThrows(IllegalStateException.class, () -> orders.createInPersonSale(sale("ref-6", "CARD", tea, 50, "10", null)));

        // live report: float 500 + cash 360 = 860 expected
        ShiftReport live = shifts.myCurrent().orElseThrow();
        assertEquals(2, live.saleCount());
        assertMoney("360.00", live.cashSales());
        assertMoney("189.00", live.otherSales());
        assertMoney("860.00", live.expectedCash());

        // a manager may discount up to the product's maximum (20%), not beyond
        as(managerUser);
        ShiftReport managerShift = shifts.open(new OpenRequest(BigDecimal.ZERO));
        assertMoney("160.00", orders.createInPersonSale(sale("ref-7", "UPI", tea, 1, "20", null)).getTotalAmount());
        assertThrows(IllegalStateException.class, () -> orders.createInPersonSale(sale("ref-8", "UPI", tea, 1, "25", null)));

        // the cashier closes: counting 850 is Nu. 10 short, which needs a note
        as(cashierUser);
        assertThrows(IllegalStateException.class, () -> shifts.close(opened.id(), new CloseRequest(new BigDecimal("850"), "")));
        ShiftReport closed = shifts.close(opened.id(), new CloseRequest(new BigDecimal("850"), "Gave wrong change"));
        assertEquals("CLOSED", closed.status());
        assertMoney("-10.00", closed.difference());
        assertThrows(IllegalStateException.class, () -> orders.createInPersonSale(sale("ref-9", "CASH", tea, 1, "10", null)),
                "closed drawer: no more sales");
        assertThrows(AccessDeniedException.class, () -> shifts.get(managerShift.id()), "a cashier cannot read another cashier's drawer");
        assertThrows(AccessDeniedException.class, () -> shifts.close(managerShift.id(), new CloseRequest(BigDecimal.ZERO, "x")));
    }

    @Autowired com.api.inventory.service.CustomerService customerService;
    @Autowired com.api.inventory.repository.CustomerRepository customerRepo;
    @Autowired com.api.inventory.service.PackageService packageService;

    @Test
    void customersAreRecognisedAndAnAdminCanMakeSomeoneASeller() {
        User boss = user("boss3@test.bt", "ADMIN", "17300009");
        User till = user("till3@test.bt", "MANAGER", "17300008");
        Long soap = item("Soap", "200.00", "200.00", 20, "10");

        // two counter sales with the same phone = one customer with two visits
        as(till);
        shifts.open(new OpenRequest(BigDecimal.ZERO));
        PosSaleRequestDTO first = sale("cust-1", "CASH", soap, 1, "0", null);
        first.setCustomerPhone("17311111");
        first.setCustomerName("Tashi");
        orders.createInPersonSale(first);
        PosSaleRequestDTO second = sale("cust-2", "CARD", soap, 2, "0", null);
        second.setCustomerPhone("17311111");
        orders.createInPersonSale(second);
        orders.createInPersonSale(sale("cust-3", "CASH", soap, 1, "0", null)); // walk-in without a phone: no record

        var tashi = customerService.byPhone("17311111").orElseThrow();
        assertEquals("Tashi", tashi.name());
        assertEquals(2, tashi.visits());
        assertMoney("600.00", tashi.totalSpent());

        // Tashi later signs up online with the same phone: the account joins the same customer record
        User account = user("tashi@test.bt", "USER", "17311111");
        customerService.linkUser(account);
        var joined = customerService.byPhone("17311111").orElseThrow();
        assertEquals(tashi.id(), joined.id());
        assertTrue(joined.hasAccount());
        assertEquals("tashi@test.bt", joined.email());

        // someone else who happens to have this phone in an order with a DIFFERENT email is not merged
        customerService.upsert(null, "Other", "other@test.bt", "17311111", null, "ONLINE", null);
        assertEquals(2, customerRepo.findAll().stream().filter(c -> "17311111".equals(c.getPhone())).count());

        // the admin makes Tashi a seller directly (registered at the counter)
        as(boss);
        AdminController.UserView seller = admin.makeSeller(account.getId(), new com.api.inventory.dto.MarketplaceDTOs.SellerApplication(
                "Tashi Crafts", "17311111", "Norzin Lam", "Thimphu", null, "Bank of Bhutan", "Tashi", "200111222",
                "11512999888", null, null, null, null));
        assertEquals("SELLER", seller.roleName());
        assertEquals("SELLER", seller.partner());
        assertEquals("APPROVED", seller.partnerStatus());

        // ...but Tashi must still accept the Seller Agreement personally before using My shop
        as(users.findByEmail("tashi@test.bt").orElseThrow());
        assertThrows(com.api.inventory.exception.TermsNotAcceptedException.class, () -> packageService.requireApprovedSeller());

        // the tests share one database: switch this extra admin off so the "last admin" test is not affected
        User extra = users.findByEmail("boss3@test.bt").orElseThrow();
        extra.setActive(false);
        users.save(extra);
    }

    // ---------- helpers ----------

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

    private Long item(String name, String mrp, String price, int qty, String maxDiscount) {
        ItemMaster item = new ItemMaster();
        item.setItemName(name);
        item.setSku("T-" + System.nanoTime());
        item.setMrp(new BigDecimal(mrp));
        item.setSellingPrice(new BigDecimal(price));
        item.setMaxDiscountPercent(new BigDecimal(maxDiscount));
        item.setDiscountAllowed(true);
        item.setIsActive(true);
        item.setCreatedAt(LocalDateTime.now());
        Long id = items.save(item).getItemId();
        InventoryStock s = new InventoryStock();
        s.setItemId(id);
        s.setCurrentQuantity(qty);
        stock.save(s);
        return id;
    }

    private static PosSaleRequestDTO sale(String ref, String method, Long itemId, int qty, String discount, String tendered) {
        PosSaleRequestDTO r = new PosSaleRequestDTO();
        r.setClientRef(ref);
        r.setPaymentMethod(method);
        r.setTaxes(List.of());
        r.setAmountTendered(tendered == null ? null : new BigDecimal(tendered));
        PosSaleRequestDTO.ItemQty line = new PosSaleRequestDTO.ItemQty();
        line.setItemId(itemId);
        line.setQuantity(qty);
        line.setMrp(new BigDecimal("200.00"));
        line.setDiscountPercent(new BigDecimal(discount));
        r.setItems(List.of(line));
        return r;
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }
}
