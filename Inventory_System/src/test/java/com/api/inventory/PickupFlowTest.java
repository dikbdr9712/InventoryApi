package com.api.inventory;

import com.api.inventory.dto.DeliveryDTOs.LocationRequest;
import com.api.inventory.dto.DeliveryDTOs.RateRequest;
import com.api.inventory.dto.MarketplaceDTOs.*;
import com.api.inventory.dto.OrderRequestDTO;
import com.api.inventory.entity.*;
import com.api.inventory.repository.*;
import com.api.inventory.security.Permissions;
import com.api.inventory.service.DeliveryPricingService;
import com.api.inventory.service.MarketplaceService;
import com.api.inventory.service.LegalTermsService;
import org.springframework.mock.web.MockMultipartFile;
import com.api.inventory.service.OrderService;
import com.api.inventory.service.PackageService;
import com.api.inventory.service.SellerItemService;
import com.api.inventory.service.SellerItemService.SellerItemForm;
import com.api.inventory.service.SalesReturnService;
import com.api.inventory.dto.ReturnDTO;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "Pick up myself": no address and no delivery fee; the customer sees where to collect each package once it is paid;
 * packed packages wait for the customer (never on the drivers' board, never given to a driver); the seller (with the
 * customer's code) or staff hand them over; the seller earns, no driver is paid. In-memory database of its own.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pickupflow;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false", // the migrations are MySQL; tests build the tables from the code
        "spring.jpa.show-sql=false",
        "app.private-upload-dir=target/test-private-uploads",
        // the sales report query uses MySQL's DATE() typing; build repositories on first use so H2 never checks it
        "spring.data.jpa.repositories.bootstrap-mode=lazy"
})
class PickupFlowTest {

    @Autowired MarketplaceService marketplace;
    @Autowired PackageService packages;
    @Autowired SellerItemService sellerItems;
    @Autowired OrderService orders;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired ItemMasterRepository items;
    @Autowired InventoryStockRepository stock;
    @Autowired OrderRepository orderRepo;
    @Autowired RiderProfileRepository riderRepo;
    @Autowired LedgerEntryRepository ledgerRepo;
    @Autowired LegalTermsService legal;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void customersCanCollectTheirOrderThemselves() {
        role("ADMIN");
        role("USER");
        User admin = user("pickadmin@test.bt", "ADMIN", "17000061");
        User shopkeeper = user("pickseller@test.bt", "USER", "17000062");
        User driver = user("pickrider@test.bt", "USER", "17000063");
        User customer = user("pickbuyer@test.bt", "USER", "17000064");

        // a seller with a pickup point, and one of our own products
        as(shopkeeper);
        PartnerView applied = marketplace.applyAsSeller(shopkeeper.getEmail(), new SellerApplication("Bumthang Cheese", "17000062",
                "Chamkhar town, near the bridge", "Bumthang", "Cheese", "Bank of Bhutan", "Bumthang Cheese", "200123999",
                "11512000999", null, null, legal.current("SELLER").getVersion(), true), withId(jpg()), null);
        as(admin);
        marketplace.setSellerStatus(applied.id(), new StatusRequest("APPROVED", null), admin.getEmail());
        User sellerUser = users.findByEmail(shopkeeper.getEmail()).orElseThrow();
        as(sellerUser);
        SellerProfile seller = packages.requireApprovedSeller();
        SellerItemForm form = new SellerItemForm();
        form.setItemName("Yak cheese");
        form.setCategory("Food");
        form.setSellingPrice(new BigDecimal("300.00"));
        form.setQuantity(5);
        Long cheese = sellerItems.create(seller, form, null).getItemId();
        Long lamp = houseItem("Butter lamp", "50.00", 4);

        // a driver whose truck could carry anything
        as(driver);
        PartnerView riderApp = marketplace.applyAsRider(driver.getEmail(), riderForm("17000063", "Pickup truck",
                java.time.LocalDate.now().plusYears(1), legal.current("RIDER").getVersion()), withIdAndLicence(), null);
        as(admin);
        marketplace.setRiderStatus(riderApp.id(), new StatusRequest("APPROVED", null), admin.getEmail());
        Long riderId = riderRepo.findByUserEmail(driver.getEmail()).orElseThrow().getId();

        // a delivery still needs an address; "Pick up myself" does not, and has no delivery fee
        as(customer);
        OrderRequestDTO noAddress = new OrderRequestDTO();
        noAddress.setCustomerName("Sonam");
        noAddress.setCustomerEmail(customer.getEmail());
        noAddress.setCustomerPhone("17000064");
        noAddress.setItems(List.of(new OrderRequestDTO.Item(lamp, 1, null)));
        assertThrows(IllegalStateException.class, () -> orders.createOrder(noAddress));

        OrderRequestDTO request = new OrderRequestDTO();
        request.setCustomerName("Sonam");
        request.setCustomerEmail(customer.getEmail());
        request.setCustomerPhone("17000064");
        request.setFulfilment("PICKUP");
        request.setItems(List.of(new OrderRequestDTO.Item(cheese, 1, null), new OrderRequestDTO.Item(lamp, 2, null)));
        Order order = orders.createOrder(request);
        assertEquals("PICKUP", order.getFulfilment());
        assertMoney("0.00", order.getDeliveryFee());
        assertMoney("400.00", order.getTotalAmount()); // 300 + 2 x 50, nothing for delivery
        List<PackageView> before = packages.forOrder(order.getOrderId(), true);
        assertEquals(2, before.size(), "one package per place to collect from");
        assertTrue(before.stream().allMatch(PackageView::selfPickup));
        assertNull(before.get(0).pickupAddress(), "where to collect is shown once it is paid");

        as(admin);
        orders.confirmPayment(order.getOrderId());
        PackageView cheesePkg = packages.forOrder(order.getOrderId(), false).stream().filter(x -> x.sellerId() != null).findFirst().orElseThrow();
        PackageView lampPkg = packages.forOrder(order.getOrderId(), false).stream().filter(x -> x.sellerId() == null).findFirst().orElseThrow();

        // paid: the customer sees where to go and whom to call, and the collection code
        as(customer);
        PackageView forCustomer = packages.forOrder(order.getOrderId(), true).stream().filter(x -> x.sellerId() != null).findFirst().orElseThrow();
        assertEquals("Chamkhar town, near the bridge, Bumthang", forCustomer.pickupAddress());
        assertEquals("17000062", forCustomer.sellerPhone());
        assertNotNull(forCustomer.deliveryCode());

        // packed: it waits for the customer; drivers never see it or get it
        as(sellerUser);
        packages.markPacked(cheesePkg.id());
        as(admin);
        packages.markPacked(lampPkg.id());
        as(users.findByEmail(driver.getEmail()).orElseThrow());
        RiderProfile truck = packages.requireApprovedRider();
        assertTrue(packages.openJobs(truck).stream().noneMatch(x -> x.orderId().equals(order.getOrderId())), "not on the job board");
        assertThrows(IllegalStateException.class, () -> packages.accept(cheesePkg.id()));
        as(admin);
        assertThrows(IllegalStateException.class, () -> packages.assignRider(cheesePkg.id(), riderId), "no driver for a pickup");
        assertThrows(IllegalStateException.class, () -> packages.pickUp(lampPkg.id()), "and no staff delivery either");

        // the seller hands theirs over with the customer's code; never another package
        as(sellerUser);
        String wrong = "0000".equals(forCustomer.deliveryCode()) ? "1111" : "0000";
        assertThrows(IllegalStateException.class, () -> packages.handOver(cheesePkg.id(), wrong));
        assertThrows(AccessDeniedException.class, () -> packages.handOver(lampPkg.id(), forCustomer.deliveryCode()));
        PackageView given = packages.handOver(cheesePkg.id(), forCustomer.deliveryCode());
        assertEquals("DELIVERED", given.status());
        assertEquals("pickseller", given.handedOverBy());
        assertEquals("CONFIRMED", orderRepo.findById(order.getOrderId()).orElseThrow().getOrderStatus(), "our package is still waiting");

        // our staff hand over ours (they checked who it is: no code needed); the order is complete
        as(admin);
        packages.handOver(lampPkg.id(), null);
        assertEquals("COMPLETED", orderRepo.findById(order.getOrderId()).orElseThrow().getOrderStatus());

        // the seller earned their share; no driver was paid
        assertMoney(cheesePkg.sellerEarning().toPlainString(), ledgerRepo.sumOf("SELLER", seller.getId(), LedgerEntry.SALE));
        assertMoney("0.00", ledgerRepo.sumOf("RIDER", riderId, LedgerEntry.DELIVERY));
    }

    private static RiderApplication riderForm(String phone, String vehicle, java.time.LocalDate expiry, int termsVersion) {
        return new RiderApplication(phone, vehicle, "BP-2-B9999", "DL-777", "Thimphu", "Bank of Bhutan", "Dorji", "200999888",
                "11512000" + phone.substring(5), expiry, "Pema", "17555555", termsVersion, true);
    }

    private static MockMultipartFile jpg() {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01});
    }

    private static MarketplaceService.ApplicationFiles withId(MockMultipartFile id) {
        return new MarketplaceService.ApplicationFiles(id, null, null);
    }

    private static MarketplaceService.ApplicationFiles withIdAndLicence() {
        return new MarketplaceService.ApplicationFiles(jpg(), jpg(), null);
    }

    private void role(String name) {
        if (roles.findByName(name).isEmpty()) {
            Role r = new Role();
            r.setName(name);
            roles.save(r);
        }
    }

    private User user(String email, String role, String phone) {
        User u = new User();
        u.setName(email.substring(0, email.indexOf('@')));
        u.setEmail(email);
        u.setPhone(phone);
        u.setPassword("x");
        u.setRole(roles.findByName(role).orElseThrow());
        return users.save(u);
    }

    private void as(User u) {
        String role = u.getRole().getName();
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        Permissions.defaultsFor(role).forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(u.getEmail(), null, authorities));
    }

    private Long houseItem(String name, String price, int qty) {
        ItemMaster item = new ItemMaster();
        item.setItemName(name);
        item.setSku("T-" + System.nanoTime());
        item.setSellingPrice(new BigDecimal(price));
        item.setMrp(new BigDecimal(price));
        item.setIsActive(true);
        item.setCreatedAt(LocalDateTime.now());
        Long id = items.save(item).getItemId();
        InventoryStock s = new InventoryStock();
        s.setItemId(id);
        s.setCurrentQuantity(qty);
        stock.save(s);
        return id;
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }
}
