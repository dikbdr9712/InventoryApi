package com.api.inventory;

import com.api.inventory.dto.OrderRequestDTO;
import com.api.inventory.dto.PaymentRequestDTO;
import com.api.inventory.dto.PosSaleRequestDTO;
import com.api.inventory.entity.*;
import com.api.inventory.repository.*;
import com.api.inventory.security.AccessControlService;
import com.api.inventory.service.EmailService;
import com.api.inventory.service.OrderService;
import com.api.inventory.service.PaymentService;
import com.api.inventory.service.PosShiftService;
import com.api.inventory.service.PosShiftService.OpenRequest;
import com.api.inventory.service.PosShiftService.ShiftReport;
import com.api.inventory.service.ReceiptService;
import com.api.inventory.service.ReceiptService.Receipt;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Journal numbers at the counter and online (required for bank transfers, never accepted twice, in the shift report)
 * and the payment receipt (online and counter, the customer's own only). In-memory database.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:journals;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false",
        "app.private-upload-dir=target/test-private-uploads",
        "spring.data.jpa.repositories.bootstrap-mode=lazy"
})
class JournalAndReceiptTest {

    @MockitoBean EmailService email;

    @Autowired AccessControlService access;
    @Autowired PosShiftService shifts;
    @Autowired OrderService orders;
    @Autowired PaymentService payments;
    @Autowired ReceiptService receipts;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired ItemMasterRepository items;
    @Autowired InventoryStockRepository stock;
    @Autowired OrderRepository orderRepo;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theCounterRecordsJournalNumbers() {
        User cashier = user("cashier@journal.bt", "CONTROLLER", "17400001");
        Long tea = item("Green tea", "200.00", 20);
        as(cashier);
        shifts.open(new OpenRequest(new BigDecimal("500")));

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> orders.createInPersonSale(sale("j-1", "BANK_TRANSFER", tea, 1, null)));
        assertTrue(missing.getMessage().contains("journal number"), missing.getMessage());
        assertThrows(IllegalArgumentException.class, () -> orders.createInPersonSale(sale("j-1b", "BANK_TRANSFER", tea, 1, "12")),
                "too short to be a journal number");

        Order transfer = orders.createInPersonSale(sale("j-2", "BANK_TRANSFER", tea, 1, "  jrnl   556677 "));
        assertEquals("JRNL 556677", orderRepo.findById(transfer.getOrderId()).orElseThrow().getPaymentReference(), "tidied");

        IllegalStateException twice = assertThrows(IllegalStateException.class,
                () -> orders.createInPersonSale(sale("j-3", "BANK_TRANSFER", tea, 1, "Jrnl 556677")));
        assertTrue(twice.getMessage().contains("already used for sale #" + transfer.getOrderId()), twice.getMessage());

        Order card = orders.createInPersonSale(sale("j-4", "CARD", tea, 1, null));
        assertNull(card.getPaymentReference(), "a card approval code is optional");
        Order cardWithCode = orders.createInPersonSale(sale("j-5", "CARD", tea, 1, "A1B2C3"));
        assertEquals("A1B2C3", cardWithCode.getPaymentReference());
        Order cash = orders.createInPersonSale(sale("j-6", "CASH", tea, 1, "IGNORED-1"));
        assertNull(cash.getPaymentReference(), "cash has no journal number");
        // the same till message twice is still the same sale, not a "journal already used"
        assertEquals(transfer.getOrderId(), orders.createInPersonSale(sale("j-2", "BANK_TRANSFER", tea, 1, "jrnl 556677")).getOrderId());

        ShiftReport report = shifts.myCurrent().orElseThrow();
        assertEquals(3, report.nonCashSales().size(), "the transfer and both card sales");
        PosShiftService.NonCashSale first = report.nonCashSales().get(0);
        assertEquals(transfer.getOrderId(), first.orderId());
        assertEquals("BANK_TRANSFER", first.method());
        assertEquals("JRNL 556677", first.reference());

        Receipt r = receipts.receipt(transfer.getOrderId());
        assertEquals("POS", r.source());
        assertEquals("Bank transfer (mobile banking)", r.payment().method());
        assertEquals("JRNL 556677", r.payment().journal());
        assertEquals("Journal no.", r.payment().journalLabel());
        assertEquals("cashier", r.servedBy());
        assertEquals(1, r.lines().size());
        assertEquals(0, r.total().compareTo(transfer.getTotalAmount()));

        Receipt cashReceipt = receipts.receipt(cash.getOrderId());
        assertEquals("Cash", cashReceipt.payment().method());
        assertNull(cashReceipt.payment().journal());
    }

    @Test
    void oneTransferPaysForOneThingOnly() {
        User cashier = user("cashier2@journal.bt", "CONTROLLER", "17400011");
        User pema = user("pema@journal.bt", "USER", "17400012");
        User dorji = user("dorji@journal.bt", "USER", "17400013");
        Long soap = item("Herbal soap", "150.00", 20);

        as(cashier);
        shifts.open(new OpenRequest(BigDecimal.ZERO));
        orders.createInPersonSale(sale("o-1", "BANK_TRANSFER", soap, 1, "MB 111222"));

        as(pema);
        Order pemaOrder = orders.createOrder(orderFor(pema, soap));
        IllegalStateException usedAtCounter = assertThrows(IllegalStateException.class, () -> payments.createPayment(transfer(pemaOrder, "mb 111222")));
        assertTrue(usedAtCounter.getMessage().contains("already used"), usedAtCounter.getMessage());
        assertEquals("MB 333444", payments.createPayment(transfer(pemaOrder, "mb  333444")).getJournalNumber());
        assertEquals("MB 333444", payments.createPayment(transfer(pemaOrder, "MB 333444")).getJournalNumber(),
                "sending the same order's payment again is not a reuse");

        as(dorji);
        Order dorjiOrder = orders.createOrder(orderFor(dorji, soap));
        assertThrows(IllegalStateException.class, () -> payments.createPayment(transfer(dorjiOrder, "MB 333444")));

        as(cashier);
        assertThrows(IllegalStateException.class, () -> orders.createInPersonSale(sale("o-2", "BANK_TRANSFER", soap, 1, "MB 333444")));
    }

    @Test
    void theReceiptIsForThePaidOrderOfItsOwner() {
        User admin = user("owner@journal.bt", "ADMIN", "17400021");
        User karma = user("karma@journal.bt", "USER", "17400022");
        User stranger = user("stranger@journal.bt", "USER", "17400023");
        Long oil = item("Hair oil", "90.00", 10);

        as(karma);
        Order order = orders.createOrder(orderFor(karma, oil));
        payments.createPayment(transfer(order, "BOB 778899"));
        IllegalStateException notYet = assertThrows(IllegalStateException.class, () -> receipts.receipt(order.getOrderId()));
        assertTrue(notYet.getMessage().contains("once the payment"), notYet.getMessage());

        as(admin);
        orders.confirmPayment(order.getOrderId());

        as(karma);
        Receipt r = receipts.receipt(order.getOrderId());
        assertEquals("ONLINE", r.source());
        assertEquals("Bank transfer", r.payment().method());
        assertEquals("BOB 778899", r.payment().journal());
        assertEquals("karma@journal.bt", r.customerEmail());
        BigDecimal delivery = r.deliveryFee() == null ? BigDecimal.ZERO : r.deliveryFee();
        assertEquals(0, r.itemsTotal().add(delivery).compareTo(r.total()), "items + delivery = total");
        assertEquals("Hair oil", r.lines().get(0).name());

        as(stranger);
        assertThrows(AccessDeniedException.class, () -> receipts.receipt(order.getOrderId()));
    }

    // ================= Helpers =================

    private static PaymentRequestDTO transfer(Order order, String journal) {
        PaymentRequestDTO p = new PaymentRequestDTO();
        p.setOrderId(order.getOrderId());
        p.setPaymentMethod("bank");
        p.setJournalNumber(journal);
        return p;
    }

    private OrderRequestDTO orderFor(User customer, Long itemId) {
        OrderRequestDTO request = new OrderRequestDTO();
        request.setCustomerName(customer.getName());
        request.setCustomerEmail(customer.getEmail());
        request.setCustomerPhone(customer.getPhone());
        request.setAddress("Motithang, Thimphu");
        request.setItems(List.of(new OrderRequestDTO.Item(itemId, 1, null)));
        return request;
    }

    private static PosSaleRequestDTO sale(String ref, String method, Long itemId, int qty, String reference) {
        PosSaleRequestDTO r = new PosSaleRequestDTO();
        r.setClientRef(ref);
        r.setPaymentMethod(method);
        r.setTaxes(List.of());
        r.setPaymentReference(reference);
        r.setAmountTendered("CASH".equals(method) ? new BigDecimal("1000") : null);
        PosSaleRequestDTO.ItemQty line = new PosSaleRequestDTO.ItemQty();
        line.setItemId(itemId);
        line.setQuantity(qty);
        r.setItems(List.of(line));
        return r;
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
        item.setSku("J-" + System.nanoTime());
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
