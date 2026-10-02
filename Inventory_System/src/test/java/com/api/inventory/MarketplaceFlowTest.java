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
 * One online order with a marketplace seller's product and one of our own, from checkout to payout.
 * Runs on an empty in-memory database, never on the real one. Money is checked to the cent.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:marketplace;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.private-upload-dir=target/test-private-uploads",
        // the sales report query uses MySQL's DATE() typing; build repositories on first use so H2 never checks it
        "spring.data.jpa.repositories.bootstrap-mode=lazy"
})
class MarketplaceFlowTest {

    @Autowired MarketplaceService marketplace;
    @Autowired PackageService packages;
    @Autowired SellerItemService sellerItems;
    @Autowired OrderService orders;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired ItemMasterRepository items;
    @Autowired InventoryStockRepository stock;
    @Autowired OrderRepository orderRepo;
    @Autowired SellerProfileRepository sellerRepo;
    @Autowired RiderProfileRepository riderRepo;
    @Autowired OrderPackageRepository packageRepo;
    @Autowired DeliveryPricingService pricing;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sellerOrderFromCheckoutToPayout() {
        role("ADMIN");
        role("USER");
        User admin = user("admin@test.bt", "ADMIN", "17000001");
        User shopkeeper = user("seller@test.bt", "USER", "17000002");
        User rider = user("rider@test.bt", "USER", "17000003");
        User customer = user("buyer@test.bt", "USER", "17000004");

        // ---- Admin sets the rules: 3% default commission; a small package costs Nu. 40 for the first 2 km,
        //      then Nu. 10 per km; the rider gets 80%. Our own shop has no map point yet.
        as(admin);
        marketplace.updateSettings(new SettingsRequest(new BigDecimal("3"),
                List.of(new RateRequest("SMALL", new BigDecimal("40"), new BigDecimal("10"))),
                new BigDecimal("2"), new BigDecimal("80"), new BigDecimal("30"), new BigDecimal("5"), null, null, null), admin.getEmail());

        // ---- A shopkeeper applies; staff cannot apply; admin approves and gives this seller 2.5%
        as(shopkeeper);
        int sellerTerms = legal.current("SELLER").getVersion();
        // not agreeing, an old agreement version, no CID copy, a fake "photo": all refused
        assertThrows(IllegalStateException.class, () -> marketplace.applyAsSeller(shopkeeper.getEmail(),
                sellerForm(null, true), withId(jpg()), null), "must accept the agreement");
        assertThrows(IllegalStateException.class, () -> marketplace.applyAsSeller(shopkeeper.getEmail(),
                sellerForm(sellerTerms + 5, true), withId(jpg()), null), "must accept the CURRENT version");
        assertThrows(IllegalStateException.class, () -> marketplace.applyAsSeller(shopkeeper.getEmail(),
                sellerForm(sellerTerms, false), withId(jpg()), null), "must confirm the details are true");
        assertThrows(IllegalStateException.class, () -> marketplace.applyAsSeller(shopkeeper.getEmail(),
                sellerForm(sellerTerms, true), MarketplaceService.ApplicationFiles.none(), null), "CID copy required");
        assertThrows(IllegalStateException.class, () -> marketplace.applyAsSeller(shopkeeper.getEmail(), sellerForm(sellerTerms, true),
                withId(new MockMultipartFile("idDocument", "cid.jpg", "image/jpeg", "not really a picture".getBytes())), null),
                "files are checked by their content, not their name");

        PartnerView applied = marketplace.applyAsSeller(shopkeeper.getEmail(), sellerForm(sellerTerms, true), withId(jpg()), null);
        assertEquals("PENDING", applied.status());
        assertEquals(sellerTerms, applied.verification().termsVersion());
        assertEquals(1, applied.verification().documents().size());
        assertTrue(legal.hasAcceptedCurrent(shopkeeper.getEmail(), "SELLER"), "the acceptance is recorded");
        assertThrows(IllegalStateException.class, () -> marketplace.applyAsRider(shopkeeper.getEmail(),
                riderForm("17000002", "Scooter", java.time.LocalDate.now().plusYears(2), legal.current("RIDER").getVersion()),
                withIdAndLicence(), null), "one account cannot be seller and rider");

        as(admin);
        assertThrows(IllegalStateException.class,
                () -> marketplace.setSellerStatus(applied.id(), new StatusRequest("REJECTED", " "), admin.getEmail()), "a refusal needs a reason");
        marketplace.setSellerStatus(applied.id(), new StatusRequest("APPROVED", null), admin.getEmail());
        marketplace.setSellerCommission(applied.id(), new CommissionRequest(new BigDecimal("2.5")));
        assertEquals("SELLER", users.findByEmail(shopkeeper.getEmail()).orElseThrow().getRole().getName());

        // ---- The seller lists a product with 10 in stock
        as(users.findByEmail(shopkeeper.getEmail()).orElseThrow());
        SellerProfile seller = packages.requireApprovedSeller();
        SellerItemForm form = new SellerItemForm();
        form.setItemName("Wild honey 500g");
        form.setCategory("Food");
        form.setSellingPrice(new BigDecimal("249.99"));
        form.setQuantity(10);
        Long honeyId = sellerItems.create(seller, form, null).getItemId();
        // the shop's pickup point on the map
        marketplace.setPickupLocation(seller, new LocationRequest(27.4728, 89.6393));
        assertEquals(10, stock.findByItemId(honeyId).orElseThrow().getCurrentQuantity());

        // ---- One of our own products
        Long soapId = houseItem("Herbal soap", "120.00", 5);

        // ---- The customer buys 3 honey + 1 soap. The cart's price (1.00) is ignored.
        as(customer);
        OrderRequestDTO request = new OrderRequestDTO();
        request.setCustomerName("Karma");
        request.setCustomerEmail(customer.getEmail());
        request.setCustomerPhone("17000004");
        request.setAddress("Changzamtog, Thimphu");
        request.setDropLatitude(27.4428);   // 0.03 degrees south of the seller: 3.34 km straight, 4.5 km by road
        request.setDropLongitude(89.6393);
        request.setItems(List.of(new OrderRequestDTO.Item(honeyId, 3, BigDecimal.ONE), new OrderRequestDTO.Item(soapId, 1, BigDecimal.ONE)));
        Order order = orders.createOrder(request);

        // honey: 4.5 km = 40 + 2.5 km x 10 = 65. soap: our shop has no map point, so 5 km is assumed = 40 + 3 x 10 = 70
        // 749.97 + 120.00 + 65 + 70 = 1004.97
        assertMoney("1004.97", order.getTotalAmount());
        assertMoney("135.00", order.getDeliveryFee());

        List<PackageView> mine = packages.forOrder(order.getOrderId(), true);
        assertEquals(2, mine.size(), "one package per seller");
        PackageView honeyPkg = mine.stream().filter(p -> p.sellerId() != null).findFirst().orElseThrow();
        PackageView soapPkg = mine.stream().filter(p -> p.sellerId() == null).findFirst().orElseThrow();
        assertEquals("PENDING_PAYMENT", honeyPkg.status());
        assertNotNull(honeyPkg.deliveryCode(), "the customer sees the code");
        assertEquals(new BigDecimal("4.5"), honeyPkg.distanceKm());
        assertFalse(honeyPkg.distanceEstimated());
        assertMoney("65.00", honeyPkg.deliveryFee());
        assertTrue(soapPkg.distanceEstimated(), "no map point for our shop: the distance is estimated");
        assertMoney("70.00", soapPkg.deliveryFee());

        // money on the honey package: 2.5% of 749.97 = 18.749 -> 18.75, seller gets 731.22
        PackageView staffView = packages.allForStaff(null).stream().filter(p -> p.id().equals(honeyPkg.id())).findFirst().orElseThrow();
        assertMoney("749.97", staffView.itemsSubtotal());
        assertMoney("18.75", staffView.commissionAmount());
        assertMoney("731.22", staffView.sellerEarning());
        assertNull(staffView.deliveryCode(), "staff never see the customer's code");

        // ---- Not paid yet: the seller cannot pack
        as(users.findByEmail(shopkeeper.getEmail()).orElseThrow());
        assertThrows(IllegalStateException.class, () -> packages.markPacked(honeyPkg.id()));

        // ---- Staff verify the payment: stock is taken, packages go to "to pack"
        as(admin);
        orders.confirmPayment(order.getOrderId());
        assertEquals(7, stock.findByItemId(honeyId).orElseThrow().getCurrentQuantity());
        assertEquals("TO_PACK", packages.forOrder(order.getOrderId(), false).get(0).status());

        // ---- The seller packs theirs; staff pack ours. The seller cannot touch our package.
        as(users.findByEmail(shopkeeper.getEmail()).orElseThrow());
        assertThrows(AccessDeniedException.class, () -> packages.markPacked(soapPkg.id()));
        packages.markPacked(honeyPkg.id());
        as(admin);
        packages.markPacked(soapPkg.id());

        // ---- A rider joins and takes the honey job
        as(rider);
        int driverTerms = legal.current("RIDER").getVersion();
        assertThrows(IllegalStateException.class, () -> marketplace.applyAsRider(rider.getEmail(),
                riderForm("17000003", "Scooter", java.time.LocalDate.now().minusDays(1), driverTerms), withIdAndLicence(), null),
                "an expired licence is refused");
        assertThrows(IllegalStateException.class, () -> marketplace.applyAsRider(rider.getEmail(),
                riderForm("17000003", "Scooter", java.time.LocalDate.now().plusYears(1), driverTerms), withId(jpg()), null),
                "a scooter driver must send the licence");
        PartnerView riderApp = marketplace.applyAsRider(rider.getEmail(),
                riderForm("17000003", "Scooter", java.time.LocalDate.now().plusYears(1), driverTerms), withIdAndLicence(), null);
        assertEquals(2, riderApp.verification().documents().size());
        as(admin);
        marketplace.setRiderStatus(riderApp.id(), new StatusRequest("APPROVED", null), admin.getEmail());
        User riderNow = users.findByEmail(rider.getEmail()).orElseThrow();
        assertEquals("RIDER", riderNow.getRole().getName());

        as(riderNow);
        RiderProfile scooter = packages.requireApprovedRider();
        List<PackageView> board = packages.openJobs(scooter);
        assertEquals(2, board.size());
        PackageView honeyJob = board.stream().filter(p -> p.id().equals(honeyPkg.id())).findFirst().orElseThrow();
        assertNull(honeyJob.customerPhone(), "the phone stays hidden until the job is taken");
        assertNull(honeyJob.dropLatitude(), "so does the exact drop point");
        assertMoney("52.00", honeyJob.riderPay()); // 80% of 65

        // a washing machine is not a job for a scooter
        OrderPackage bulky = packageRepo.findById(soapPkg.id()).orElseThrow();
        bulky.setDeliverySize("BULKY");
        packageRepo.save(bulky);
        assertEquals(1, packages.openJobs(scooter).size(), "a scooter rider does not see bulky jobs");
        assertThrows(IllegalStateException.class, () -> packages.accept(soapPkg.id()));

        PackageView taken = packages.accept(honeyPkg.id());
        assertEquals("17000004", taken.customerPhone(), "after accepting, the rider can call the customer");
        assertNotNull(taken.dropLatitude(), "and see where to go");
        packages.pickUp(honeyPkg.id());
        assertEquals("SHIPPED", orderRepo.findById(order.getOrderId()).orElseThrow().getOrderStatus());

        assertThrows(IllegalStateException.class, () -> packages.deliver(honeyPkg.id(), "0000".equals(honeyPkg.deliveryCode()) ? "1111" : "0000"),
                "a wrong code is refused");
        packages.deliver(honeyPkg.id(), honeyPkg.deliveryCode());

        // ---- Our own package: staff deliver it themselves (no rider pay)
        as(admin);
        packages.pickUp(soapPkg.id());
        packages.deliver(soapPkg.id(), null);
        assertEquals("COMPLETED", orderRepo.findById(order.getOrderId()).orElseThrow().getOrderStatus());

        // ---- A new Seller Agreement: the seller must accept it before using the dashboard again
        as(admin);
        legal.publish("SELLER", new LegalTermsService.PublishRequest(null,
                legal.current("SELLER").getBody() + "\n\n## 11. New clause\n- Packages must be packed within one working day.", "Added a packing deadline."));
        as(users.findByEmail(shopkeeper.getEmail()).orElseThrow());
        assertThrows(com.api.inventory.exception.TermsNotAcceptedException.class, () -> packages.requireApprovedSeller());
        legal.accept(shopkeeper.getEmail(), "SELLER", legal.current("SELLER").getVersion(), null);
        assertNotNull(packages.requireApprovedSeller());
        as(admin);

        // ---- The books: we owe the seller 731.22 and the rider 52.00
        Long sellerId = sellerRepo.findByUserEmail(shopkeeper.getEmail()).orElseThrow().getId();
        Long riderId = riderRepo.findByUserEmail(rider.getEmail()).orElseThrow().getId();
        BalanceRow sellerRow = marketplace.balances().stream().filter(b -> b.partyType().equals("SELLER")).findFirst().orElseThrow();
        assertMoney("731.22", sellerRow.balanceOwed());
        assertMoney("52.00", marketplace.balances().stream().filter(b -> b.partyType().equals("RIDER")).findFirst().orElseThrow().balanceOwed());
        assertMoney("18.75", marketplace.overview().commissionEarned());

        // ---- Payouts: never more than owed; a part payment leaves the rest owed
        assertThrows(IllegalStateException.class,
                () -> marketplace.recordPayout(new PayoutRequest("SELLER", sellerId, new BigDecimal("731.23"), "JRN-1"), admin.getEmail()));
        marketplace.recordPayout(new PayoutRequest("SELLER", sellerId, new BigDecimal("700.00"), "JRN-2"), admin.getEmail());
        marketplace.recordPayout(new PayoutRequest("RIDER", riderId, new BigDecimal("52.00"), "JRN-3"), admin.getEmail());

        EarningsSummary sellerMoney = marketplace.earnings("SELLER", sellerId, packages.rawForSeller(sellerId));
        assertMoney("31.22", sellerMoney.balanceOwed());
        assertMoney("731.22", sellerMoney.totalEarned());
        assertMoney("700.00", sellerMoney.totalPaidOut());
        assertEquals(1, sellerMoney.deliveredCount());
        assertMoney("0.00", marketplace.earnings("RIDER", riderId, packages.rawForRider(riderId)).balanceOwed());
    }

    @Test
    void cannotBuyMoreThanInStockOrFromASuspendedSeller() {
        role("USER");
        User customer = user("buyer2@test.bt", "USER", "17000010");
        Long soapId = houseItem("Bath salt", "80.00", 2);

        as(customer);
        OrderRequestDTO request = new OrderRequestDTO();
        request.setCustomerEmail(customer.getEmail());
        request.setAddress("Thimphu");
        request.setItems(List.of(new OrderRequestDTO.Item(soapId, 3, null)));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> orders.createOrder(request));
        assertTrue(e.getMessage().startsWith("Only 2 of Bath salt left"), e.getMessage());
    }

    @Test
    void deliveryPriceGrowsWithSizeAndDistance() {
        role("ADMIN");
        User admin = user("admin3@test.bt", "ADMIN", "17000020");
        as(admin);
        marketplace.updateSettings(new SettingsRequest(new BigDecimal("3"), List.of(
                new RateRequest("SMALL", new BigDecimal("40"), new BigDecimal("10")),
                new RateRequest("MEDIUM", new BigDecimal("80"), new BigDecimal("12")),
                new RateRequest("LARGE", new BigDecimal("200"), new BigDecimal("20")),
                new RateRequest("BULKY", new BigDecimal("500"), new BigDecimal("35"))),
                new BigDecimal("2"), new BigDecimal("80"), new BigDecimal("30"), new BigDecimal("5"),
                "Norzin Lam, Thimphu", 27.4728, 89.6393), admin.getEmail());

        DeliveryPricingService.Point shop = new DeliveryPricingService.Point(27.4728, 89.6393);
        DeliveryPricingService.Point near = new DeliveryPricingService.Point(27.4428, 89.6393); // 4.5 km by road
        DeliveryPricingService.Point far = new DeliveryPricingService.Point(27.1728, 89.6393);  // about 45 km by road

        DeliveryPricingService.Price small = pricing.price(DeliverySize.SMALL, shop, near);
        assertEquals(new BigDecimal("4.5"), small.distanceKm());
        assertMoney("65.00", small.fee());        // 40 + 2.5 x 10
        assertMoney("52.00", small.riderPay());   // 80%

        DeliveryPricingService.Price bulky = pricing.price(DeliverySize.BULKY, shop, near);
        assertMoney("590.00", bulky.fee());       // 500 + 2.5 x 35 = 587.50, rounded up to the next Nu. 5
        assertMoney("472.00", bulky.riderPay());

        DeliveryPricingService.Price close = pricing.price(DeliverySize.SMALL, shop, shop);
        assertMoney("40.00", close.fee());        // within the included km: only the base fee

        DeliveryPricingService.Price unknown = pricing.price(DeliverySize.MEDIUM, shop, null);
        assertTrue(unknown.estimated());
        assertMoney("120.00", unknown.fee());     // 80 + 3 km x 12 = 116, rounded up to 120

        IllegalStateException tooFar = assertThrows(IllegalStateException.class, () -> pricing.price(DeliverySize.SMALL, shop, far));
        assertTrue(tooFar.getMessage().contains("up to 30 km"), tooFar.getMessage());

        // which vehicle carries what
        assertEquals(DeliverySize.SMALL, DeliverySize.carriedBy("Bicycle"));
        assertEquals(DeliverySize.SMALL, DeliverySize.carriedBy("On foot"));
        assertEquals(DeliverySize.MEDIUM, DeliverySize.carriedBy("Scooter"));
        assertEquals(DeliverySize.MEDIUM, DeliverySize.carriedBy("Motorbike"));
        assertEquals(DeliverySize.LARGE, DeliverySize.carriedBy("Taxi"));
        assertEquals(DeliverySize.BULKY, DeliverySize.carriedBy("Pickup truck"));

        // nonsense map points and sizes are refused
        assertThrows(IllegalArgumentException.class, () -> DeliveryPricingService.point(120.0, 89.0));
        assertThrows(IllegalArgumentException.class, () -> DeliverySize.parse("HUGE"));
    }

    // ---------- helpers ----------

    @Autowired LegalTermsService legal;

    private static SellerApplication sellerForm(Integer termsVersion, boolean confirm) {
        return new SellerApplication("Paro Honey", "17000002", "Shop 4, Paro town", "Paro", "Wild honey",
                "Bank of Bhutan", "Paro Honey", "200123456", "11512000123", "TL-1234", null, termsVersion, confirm);
    }

    private static RiderApplication riderForm(String phone, String vehicle, java.time.LocalDate expiry, int termsVersion) {
        return new RiderApplication(phone, vehicle, "BP-2-B9999", "DL-777", "Thimphu", "Bank of Bhutan", "Dorji", "200999888",
                "11512000" + phone.substring(5), expiry, "Pema", "17555555", termsVersion, true);
    }

    /** A tiny file that starts like a real JPG. */
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

    /** Signs this person in for the code that follows, with their role's permissions. */
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
