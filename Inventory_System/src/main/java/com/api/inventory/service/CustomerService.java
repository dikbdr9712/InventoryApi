package com.api.inventory.service;

import com.api.inventory.entity.Customer;
import com.api.inventory.entity.Order;
import com.api.inventory.entity.User;
import com.api.inventory.repository.CustomerRepository;
import com.api.inventory.repository.OrderRepository;
import com.api.inventory.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Customers: one record per person, whether they buy online or at the counter.
 *
 * Recognising the same person: their account first, then their email, then their phone number.
 * A phone number is only joined to an existing customer when it cannot belong to someone else
 * (that customer has no other account or email), so two people sharing a phone are never merged by mistake.
 *
 * On start-up, orders that have no customer yet (all orders from before this existed) are linked once.
 */
@Service
public class CustomerService implements ApplicationRunner {

    private final CustomerRepository customers;
    private final OrderRepository orders;
    private final UserRepository users;
    private final AuditService audit;

    public CustomerService(CustomerRepository customers, OrderRepository orders, UserRepository users, AuditService audit) {
        this.customers = customers;
        this.orders = orders;
        this.users = users;
        this.audit = audit;
    }

    public record CustomerSummary(Long id, String name, String phone, String email, String address, boolean hasAccount,
                                  long visits, BigDecimal totalSpent, Instant lastSeenAt, String firstSource, Instant createdAt) {
    }

    public record CustomerOrder(Long orderId, LocalDateTime createdAt, String source, String orderStatus, String paymentStatus,
                                String paymentMethod, BigDecimal totalAmount) {
    }

    public record CustomerDetail(CustomerSummary customer, String notes, List<CustomerOrder> orders) {
    }

    public record CustomerUpdate(String name, String phone, String email, String address, String notes) {
    }

    // ================= recognising and linking =================

    /** Finds the person (or creates them) and brings their details up to date. Returns null when there is nothing to go on. */
    @Transactional
    public Customer upsert(Long userId, String name, String email, String phone, String address, String source, Instant seenAt) {
        String n = clean(name, 120);
        String e = email == null || email.isBlank() ? null : email.trim().toLowerCase();
        String p = phone == null ? null : phone.replaceAll("\\D", "");
        if (p != null && !p.matches("^[0-9]{8}$")) {
            p = null;
        }
        String a = clean(address, 500);
        if (userId == null && e == null && p == null) {
            return null; // a walk-in who gave nothing: no customer record
        }

        Customer c = null;
        if (userId != null) {
            c = customers.findFirstByUserId(userId).orElse(null);
        }
        if (c == null && e != null) {
            c = customers.findFirstByEmailIgnoreCaseOrderByIdAsc(e)
                    .filter(found -> userId == null || found.getUserId() == null || found.getUserId().equals(userId))
                    .orElse(null);
        }
        if (c == null && p != null) {
            final String email0 = e;
            c = customers.findFirstByPhoneOrderByIdAsc(p)
                    // only the same person: no other account, and no other email
                    .filter(found -> (userId == null || found.getUserId() == null || found.getUserId().equals(userId))
                            && (email0 == null || found.getEmail() == null || found.getEmail().equalsIgnoreCase(email0)))
                    .orElse(null);
        }

        Instant when = seenAt == null ? Instant.now() : seenAt;
        if (c == null) {
            c = new Customer();
            c.setFirstSource(source);
            c.setCreatedAt(when);
        }
        if (userId != null && c.getUserId() == null) c.setUserId(userId);
        if (e != null && c.getEmail() == null) c.setEmail(e);
        if (p != null && c.getPhone() == null) c.setPhone(p);
        if (n != null && (c.getName() == null || c.getName().isBlank())) c.setName(n);
        if (a != null) c.setAddress(a); // the latest delivery address is the useful one
        if (c.getLastSeenAt() == null || when.isAfter(c.getLastSeenAt())) c.setLastSeenAt(when);
        return customers.save(c);
    }

    /** Links an order to its customer (called when the order is saved). */
    @Transactional
    public void linkOrder(Order order) {
        if (order == null || order.getCustomerId() != null) {
            return;
        }
        Long userId = order.getCustomerEmail() == null ? null
                : users.findByEmail(order.getCustomerEmail()).map(User::getId).orElse(null);
        Instant when = order.getCreatedAt() == null ? Instant.now() : order.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant();
        Customer c = upsert(userId, order.getCustomerName(), order.getCustomerEmail(), order.getCustomerPhone(), order.getAddress(),
                "POS".equalsIgnoreCase(order.getSource()) ? "POS" : "ONLINE", when);
        if (c != null) {
            order.setCustomerId(c.getId());
            orders.linkCustomer(order.getOrderId(), c.getId());
        }
    }

    /** A new account: the customer record is created (or joined to their earlier counter visits). */
    @Transactional
    public void linkUser(User user) {
        upsert(user.getId(), user.getName(), user.getEmail(), user.getPhone(), null, "SIGNUP", Instant.now());
    }

    /** Once, on start-up: every older order gets its customer; every customer account gets a record. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Order> unlinked = new ArrayList<>(orders.findByCustomerIdIsNull());
        unlinked.sort(Comparator.comparing(Order::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())));
        int linked = 0;
        for (Order o : unlinked) {
            if (o.getCustomerEmail() != null || o.getCustomerPhone() != null) {
                linkOrder(o);
                if (o.getCustomerId() != null) linked++;
            }
        }
        for (User u : users.findAll()) {
            if (u.getRole() != null && "USER".equalsIgnoreCase(u.getRole().getName()) && customers.findFirstByUserId(u.getId()).isEmpty()) {
                linkUser(u);
            }
        }
        if (linked > 0) {
            System.out.println("Customers: linked " + linked + " earlier orders to their customers.");
        }
    }

    // ================= reading =================

    public List<CustomerSummary> list(String query, int limit) {
        var page = PageRequest.of(0, Math.max(1, Math.min(limit, 500)));
        String q = query == null ? "" : query.trim();
        List<Customer> found = q.isEmpty() ? customers.findAllByOrderByLastSeenAtDesc(page) : customers.search(q, page);
        return summaries(found);
    }

    public CustomerDetail detail(Long id) {
        Customer c = customers.findById(id).orElseThrow(() -> new IllegalStateException("Customer not found."));
        List<CustomerOrder> history = orders.findByCustomerIdOrderByCreatedAtDesc(id).stream()
                .map(o -> new CustomerOrder(o.getOrderId(), o.getCreatedAt(), o.getSource(), o.getOrderStatus(), o.getPaymentStatus(),
                        o.getPaymentMethod(), o.getTotalAmount()))
                .toList();
        return new CustomerDetail(summaries(List.of(c)).get(0), c.getNotes(), history);
    }

    /** For the till: what we know about this phone number. */
    public Optional<CustomerSummary> byPhone(String phone) {
        return customers.findFirstByPhoneOrderByIdAsc(phone).map(c -> summaries(List.of(c)).get(0));
    }

    @Transactional
    public CustomerDetail update(Long id, CustomerUpdate u) {
        Customer c = customers.findById(id).orElseThrow(() -> new IllegalStateException("Customer not found."));
        String name = clean(u.name(), 120);
        if (name == null) {
            throw new IllegalStateException("Enter the customer's name.");
        }
        String phone = u.phone() == null || u.phone().isBlank() ? null : u.phone().replaceAll("\\s", "");
        if (phone != null && !phone.matches("^[0-9]{8}$")) {
            throw new IllegalStateException("Enter an 8-digit phone number, or leave it empty.");
        }
        String email = u.email() == null || u.email().isBlank() ? null : u.email().trim().toLowerCase();
        if (email != null && !email.matches("^\\S+@\\S+\\.\\S+$")) {
            throw new IllegalStateException("Enter a valid email address, or leave it empty.");
        }
        if (c.getUserId() != null && email != null && c.getEmail() != null && !email.equalsIgnoreCase(c.getEmail())) {
            throw new IllegalStateException("This customer signs in with " + c.getEmail() + ". They change their email themselves.");
        }
        c.setName(name);
        c.setPhone(phone);
        c.setEmail(email == null && c.getUserId() != null ? c.getEmail() : email);
        c.setAddress(clean(u.address(), 500));
        c.setNotes(clean(u.notes(), 1000));
        customers.save(c);
        audit.record("CUSTOMER_UPDATED", "customer " + c.getId() + " (" + c.getName() + ")", null);
        return detail(id);
    }

    // ================= helpers =================

    private List<CustomerSummary> summaries(List<Customer> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, Object[]> stats = new HashMap<>();
        for (Object[] row : orders.statsForCustomers(list.stream().map(Customer::getId).toList())) {
            stats.put((Long) row[0], row);
        }
        List<CustomerSummary> result = new ArrayList<>();
        for (Customer c : list) {
            Object[] s = stats.get(c.getId());
            long visits = s == null ? 0 : ((Number) s[1]).longValue();
            BigDecimal spent = s == null || s[2] == null ? BigDecimal.ZERO : (BigDecimal) s[2];
            result.add(new CustomerSummary(c.getId(), c.getName(), c.getPhone(), c.getEmail(), c.getAddress(), c.getUserId() != null,
                    visits, spent, c.getLastSeenAt(), c.getFirstSource(), c.getCreatedAt()));
        }
        return result;
    }

    private static String clean(String value, int max) {
        String v = value == null ? "" : value.trim();
        return v.isEmpty() ? null : v.substring(0, Math.min(max, v.length()));
    }
}
