package com.api.inventory.service;

import com.api.inventory.entity.*;
import com.api.inventory.repository.NotificationRepository;
import com.api.inventory.repository.RiderProfileRepository;
import com.api.inventory.repository.SellerProfileRepository;
import com.api.inventory.repository.UserRepository;
import com.api.inventory.security.AccessControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tells people what happened: a message in their list (the bell), and for important things an email,
 * and for "your order is on the way" a text message.
 *
 *   Customers  order received, payment confirmed or not, rider on the way, delivered, cancelled
 *   Sellers    new paid order to pack, collected, delivered (money booked), payout sent, application decision
 *   Riders     new job their vehicle can carry, delivered (money booked), payout sent, application decision
 *   Staff      payments to check, our own packages to pack, new seller/driver applications
 *
 * Saving the message is part of the action's transaction; emails and texts go out only after it is saved.
 * A notification problem is logged and never stops the action itself.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public record Note(String type, String title, String body, String link) {
    }

    public record NotificationView(Long id, String type, String title, String body, String link, Instant createdAt, boolean read) {
    }

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final AccessControlService access;
    private final SellerProfileRepository sellers;
    private final RiderProfileRepository riders;
    private final EmailService email;
    private final SmsService sms;

    /** The address of the website, for links in emails. */
    @Value("${app.public-url:http://localhost:4200}")
    private String publicUrl;

    public NotificationService(NotificationRepository notifications, UserRepository users, AccessControlService access,
                               SellerProfileRepository sellers, RiderProfileRepository riders, EmailService email, SmsService sms) {
        this.notifications = notifications;
        this.users = users;
        this.access = access;
        this.sellers = sellers;
        this.riders = riders;
        this.email = email;
        this.sms = sms;
    }

    // ================= Sending =================

    /** One person, by their account email. sendEmail = also by email. */
    public void user(String userEmail, Note note, boolean sendEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return;
        }
        try {
            Notification n = new Notification();
            n.setUserEmail(cut(userEmail.trim(), 120));
            n.setType(cut(note.type(), 40));
            n.setTitle(cut(note.title(), 160));
            n.setBody(cut(note.body(), 500));
            n.setLink(cut(note.link(), 200));
            n.setCreatedAt(Instant.now());
            notifications.save(n);
            if (sendEmail) {
                email.sendEmail(userEmail, note.title(), emailText(note));
            }
        } catch (RuntimeException e) {
            log.warn("Notification '{}' for {} not saved: {}", note.type(), userEmail, e.getMessage());
        }
    }

    /** The customer of an order; smsText (optional) also goes as a text message to the order's phone. */
    public void customer(Order order, Note note, boolean sendEmail, String smsText) {
        if (order == null) {
            return;
        }
        user(order.getCustomerEmail(), note, sendEmail);
        if (smsText != null) {
            sms.send(order.getCustomerPhone(), smsText);
        }
    }

    /** Everyone whose role has this permission (for example payments.verify), active accounts only. */
    public void withPermission(String permission, Note note, boolean sendEmail) {
        Set<String> emails = new LinkedHashSet<>();
        for (User u : users.findAll()) {
            if (u.isActive() && u.getRole() != null && access.permissionsOf(u.getRole().getName()).contains(permission)) {
                emails.add(u.getEmail());
            }
        }
        emails.forEach(e -> user(e, note, sendEmail));
    }

    public void seller(Long sellerId, Note note, boolean sendEmail) {
        if (sellerId != null) {
            sellers.findById(sellerId).ifPresent(s -> user(s.getUser().getEmail(), note, sendEmail));
        }
    }

    public void rider(Long riderId, Note note, boolean sendEmail) {
        if (riderId != null) {
            riders.findById(riderId).ifPresent(r -> user(r.getUser().getEmail(), note, sendEmail));
        }
    }

    /** Approved riders whose vehicle can carry a package of this size (in the list only: jobs come often). */
    public void ridersWhoCanCarry(DeliverySize size, Note note) {
        for (RiderProfile r : riders.findAll()) {
            if (r.isApproved() && r.getUser() != null && r.getUser().isActive() && !r.licenceExpired()
                    && size.fits(r.getVehicleType())) {
                user(r.getUser().getEmail(), note, false);
            }
        }
    }

    // ================= Reading (the bell) =================

    public List<NotificationView> latest(String userEmail, int limit) {
        return notifications.findByUserEmailOrderByCreatedAtDesc(userEmail, PageRequest.of(0, Math.max(1, Math.min(limit, 100))))
                .stream()
                .map(n -> new NotificationView(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(), n.getCreatedAt(), n.getReadAt() != null))
                .toList();
    }

    public long unread(String userEmail) {
        return notifications.countByUserEmailAndReadAtIsNull(userEmail);
    }

    @Transactional
    public void markRead(String userEmail, Long id) {
        notifications.findByIdAndUserEmail(id, userEmail).ifPresent(n -> {
            if (n.getReadAt() == null) {
                n.setReadAt(Instant.now());
                notifications.save(n);
            }
        });
    }

    @Transactional
    public int markAllRead(String userEmail) {
        return notifications.markAllRead(userEmail, Instant.now());
    }

    /** Every night: messages older than 90 days are deleted. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void cleanUp() {
        int removed = notifications.deleteOlderThan(Instant.now().minus(Duration.ofDays(90)));
        if (removed > 0) {
            log.info("Removed {} notifications older than 90 days", removed);
        }
    }

    // ================= Helpers =================

    private String emailText(Note note) {
        StringBuilder text = new StringBuilder();
        text.append(note.title()).append("\n\n");
        if (note.body() != null && !note.body().isBlank()) {
            text.append(note.body()).append("\n\n");
        }
        if (note.link() != null && !note.link().isBlank()) {
            text.append("Open: ").append(trimSlash(publicUrl)).append(note.link()).append("\n\n");
        }
        text.append("DP DrukBazaars\n");
        text.append("You get this email because of your account or order with us.");
        return text.toString();
    }

    private static String trimSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
