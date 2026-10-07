package com.api.inventory;

import com.api.inventory.dto.OrderRequestDTO;
import com.api.inventory.entity.*;
import com.api.inventory.repository.*;
import com.api.inventory.security.AccessControlService;
import com.api.inventory.service.*;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Ratings and reviews: only what was delivered can be rated, one review per customer per product (rating again
 * changes it), service and delivery per order, staff hide and reply, low ratings reach staff. Also: the pages for
 * everyone work without signing in, and "forgot password" says whether email works. In-memory database.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reviews;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false",
        "app.private-upload-dir=target/test-private-uploads",
        "spring.data.jpa.repositories.bootstrap-mode=lazy"
})
class ReviewTest {

    @MockitoBean EmailService email;

    @Autowired WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") Filter securityChain;
    @Autowired AccessControlService access;
    @Autowired ReviewService reviews;
    @Autowired OrderService orders;
    @Autowired PackageService packages;
    @Autowired NotificationService notifications;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired ItemMasterRepository items;
    @Autowired InventoryStockRepository stock;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void onlyDeliveredProductsAreRatedAndStaffLookAfterReviews() {
        User boss = user("boss@reviews.bt", "Pema Choden", "MANAGER", "17600001");
        User karma = user("karma@reviews.bt", "Karma Tshering Dorji", "USER", "17600002");
        User stranger = user("other@reviews.bt", "Other", "USER", "17600003");
        Long honey = item("Wild honey", "400.00");

        as(karma);
        Order order = orders.createOrder(orderFor(karma, honey));

        // not paid / not delivered yet: nothing to rate
        ReviewService.OrderRating before = reviews.forOrder(order.getOrderId());
        assertFalse(before.canRate());
        assertFalse(before.items().get(0).delivered());
        assertThrows(IllegalStateException.class, () -> reviews.rateProduct(order.getOrderId(), honey, 5, "Lovely"));
        assertThrows(IllegalStateException.class, () -> reviews.rateService(order.getOrderId(), 5, 5, null));

        // paid, packed, delivered by our staff
        as(boss);
        orders.confirmPayment(order.getOrderId());
        Long pkg = packages.forOrder(order.getOrderId(), false).get(0).id();
        packages.markPacked(pkg);
        packages.pickUp(pkg);
        packages.deliver(pkg, null);

        as(stranger);
        assertThrows(AccessDeniedException.class, () -> reviews.forOrder(order.getOrderId()));
        assertThrows(AccessDeniedException.class, () -> reviews.rateProduct(order.getOrderId(), honey, 1, "Fake"));

        as(karma);
        ReviewService.OrderRating after = reviews.forOrder(order.getOrderId());
        assertTrue(after.canRate());
        assertTrue(after.items().get(0).delivered());
        assertThrows(IllegalArgumentException.class, () -> reviews.rateProduct(order.getOrderId(), honey, 6, null));
        assertThrows(IllegalArgumentException.class, () -> reviews.rateProduct(order.getOrderId(), honey, 4, "x".repeat(1001)));

        reviews.rateProduct(order.getOrderId(), honey, 5, "  Real wild honey, thick and fresh.  ");
        ReviewService.ProductSummary s = reviews.product(honey);
        assertEquals(1, s.count());
        assertEquals(0, new BigDecimal("5.0").compareTo(s.average()));
        assertEquals("Karma D.", s.reviews().get(0).displayName(), "first name and initial only");
        assertEquals("Real wild honey, thick and fresh.", s.reviews().get(0).comment());

        // rating again changes the review, it does not add a second one
        reviews.rateProduct(order.getOrderId(), honey, 2, "The jar arrived sticky.");
        s = reviews.product(honey);
        assertEquals(1, s.count());
        assertEquals(1, s.distribution()[1], "one 2-star review");
        assertEquals(2, reviews.forOrder(order.getOrderId()).items().get(0).myRating());
        assertTrue(kinds(boss).contains("LOW_RATING"), "a low rating reaches staff");
        assertEquals(1, reviews.summaries().stream().filter(x -> x.itemId().equals(honey)).count());

        reviews.rateService(order.getOrderId(), 5, 4, "Fast and friendly.");
        ReviewService.ServiceSummary service = reviews.service();
        assertEquals(1, service.count());
        assertEquals(0, new BigDecimal("4.0").compareTo(service.deliveryAverage()));
        assertEquals(new ReviewService.MyFeedback(5, 4, "Fast and friendly."), reviews.forOrder(order.getOrderId()).feedback());

        // staff: customers cannot moderate
        assertThrows(AccessDeniedException.class, () -> reviews.overview());

        as(boss);
        ReviewService.AdminOverview overview = reviews.overview();
        Long reviewId = overview.productReviews().get(0).id();
        assertEquals("karma@reviews.bt", overview.productReviews().get(0).userEmail(), "staff see who wrote it");
        reviews.replyToProductReview(reviewId, "Sorry about the jar. We now wrap each one twice.");
        assertTrue(kinds(karma).contains("REVIEW_REPLY"), "the customer hears about the answer");
        assertEquals("Sorry about the jar. We now wrap each one twice.", reviews.product(honey).reviews().get(0).reply());

        reviews.setProductReviewHidden(reviewId, true);
        assertEquals(0, reviews.product(honey).count(), "a hidden review is neither shown nor counted");
        reviews.setProductReviewHidden(reviewId, false);
        assertEquals(1, reviews.product(honey).count());

        Long feedbackId = overview.feedback().get(0).id();
        reviews.setFeedbackHidden(feedbackId, true);
        assertEquals(0, reviews.service().count());
    }

    @Test
    void reviewsAndPasswordHelpWorkWithoutSigningIn() throws Exception {
        MockMvc http = MockMvcBuilders.webAppContextSetup(context).addFilters(securityChain).build();
        Long soap = item("Herbal soap", "150.00");
        http.perform(get("/api/reviews/products/" + soap)).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));
        http.perform(get("/api/reviews/summary")).andExpect(status().isOk());
        http.perform(get("/api/reviews/service")).andExpect(status().isOk()).andExpect(jsonPath("$.count").isNumber());
        http.perform(get("/api/reviews/orders/1")).andExpect(status().isUnauthorized());
        http.perform(get("/api/reviews/admin")).andExpect(status().isUnauthorized());
        // emails cannot go out here (caught by the test): the page will not offer the email way
        http.perform(get("/api/auth/forgot-password")).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(false)).andExpect(jsonPath("$.sms").isBoolean());
    }

    // ================= Helpers =================

    private List<String> kinds(User u) {
        return notifications.latest(u.getEmail(), 50).stream().map(NotificationService.NotificationView::type).toList();
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

    private User user(String email, String name, String role, String phone) {
        User u = new User();
        u.setName(name);
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

    private Long item(String name, String price) {
        ItemMaster item = new ItemMaster();
        item.setItemName(name);
        item.setSku("RV-" + System.nanoTime());
        item.setMrp(new BigDecimal(price));
        item.setSellingPrice(new BigDecimal(price));
        item.setIsActive(true);
        item.setCreatedAt(LocalDateTime.now());
        Long id = items.save(item).getItemId();
        InventoryStock s = new InventoryStock();
        s.setItemId(id);
        s.setCurrentQuantity(20);
        stock.save(s);
        return id;
    }
}
