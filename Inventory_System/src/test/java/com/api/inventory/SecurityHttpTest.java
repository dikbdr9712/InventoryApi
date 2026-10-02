package com.api.inventory;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real HTTP requests through the whole security chain (no network port needed):
 * the public shop window stays open, everything else needs sign-in and the right permission,
 * and switching an account off signs it out on the next click.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:securityhttp;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.private-upload-dir=target/test-private-uploads",
        "spring.data.jpa.repositories.bootstrap-mode=lazy"
})
class SecurityHttpTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    Filter securityChain;

    @Autowired
    com.api.inventory.repository.UserRepository users;

    @Autowired
    com.api.inventory.repository.RoleRepository roles;

    @Autowired
    org.springframework.security.crypto.password.PasswordEncoder encoder;

    MockMvc http;

    @BeforeEach
    void setUp() {
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(securityChain).build();
    }

    @Test
    void publicShopWindowIsOpen() throws Exception {
        http.perform(get("/api/items/allItems")).andExpect(status().isOk());
        http.perform(get("/api/marketplace/settings")).andExpect(status().isOk());
        // the cart shows the delivery price before anyone signs in
        http.perform(get("/api/delivery/areas")).andExpect(status().isOk());
        http.perform(post("/api/delivery/quote").contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalFee").value(0));
    }

    @Test
    void everythingElseNeedsSignIn() throws Exception {
        http.perform(get("/api/reports/sales")).andExpect(status().isUnauthorized());          // was open before
        http.perform(get("/api/reports/sales/summary")).andExpect(status().isUnauthorized());
        http.perform(get("/api/admin/orders")).andExpect(status().isUnauthorized());
        http.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        http.perform(get("/api/pos/shifts/current")).andExpect(status().isUnauthorized());
        http.perform(get("/api/returns/summary")).andExpect(status().isUnauthorized());
        http.perform(get("/api/orders/1/returnable")).andExpect(status().isUnauthorized());
        http.perform(get("/api/customers/lookup").param("phone", "17000000")).andExpect(status().isUnauthorized());
        http.perform(get("/api/delivery/admin/areas")).andExpect(status().isUnauthorized());
        http.perform(put("/api/seller/location").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotReachStaffToolsAndASwitchedOffAccountIsSignedOut() throws Exception {
        var user = new com.api.inventory.entity.User();
        user.setName("Karma");
        user.setEmail("karma@http.bt");
        user.setPhone("17888001");
        user.setPassword(encoder.encode("secret1"));
        user.setActive(true);
        user.setRole(roles.findByName("USER").orElseThrow());
        users.save(user);

        MockHttpSession session = (MockHttpSession) http.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"karma@http.bt\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isArray())
                .andReturn().getRequest().getSession(false);

        http.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
        http.perform(get("/api/reports/sales").session(session)).andExpect(status().isForbidden());
        http.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
        http.perform(get("/api/pos/shifts/current").session(session)).andExpect(status().isForbidden());
        http.perform(post("/api/orders/pos/sale").session(session).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        // wrong password: 401 with a message, not a server error
        http.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"karma@http.bt\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized());

        // broken JSON: a clear 400
        http.perform(post("/api/legal/terms/SELLER/accept").session(session).contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        // the application form needs the multipart format: plain JSON gets a clear 415, not a server error
        http.perform(post("/api/marketplace/apply/seller").session(session).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType());

        // the agreements can be read without signing in
        http.perform(get("/api/legal/terms/SELLER")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        http.perform(get("/api/legal/terms/DRIVER")).andExpect(status().isOk());
        http.perform(get("/api/legal/admin/terms").session(session)).andExpect(status().isForbidden());
        http.perform(post("/api/delivery/admin/areas").session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"town\":\"Y\",\"latitude\":27.4,\"longitude\":89.6}")).andExpect(status().isForbidden());

        // an admin switches the account off: the very next request is anonymous, and sign-in is refused
        user = users.findByEmail("karma@http.bt").orElseThrow();
        user.setActive(false);
        users.save(user);
        http.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        http.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"karma@http.bt\",\"password\":\"secret1\"}"))
                .andExpect(status().isForbidden());
    }
}
