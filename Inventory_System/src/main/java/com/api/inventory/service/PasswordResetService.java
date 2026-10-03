package com.api.inventory.service;

import com.api.inventory.entity.PasswordResetToken;
import com.api.inventory.entity.User;
import com.api.inventory.repository.PasswordResetTokenRepository;
import com.api.inventory.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Forgot password": a link by email to choose a new password.
 *
 *  - The answer never says whether an email has an account (nobody can test which emails are registered).
 *  - The link holds 32 random bytes; only their SHA-256 fingerprint is stored. It works once, for 30 minutes,
 *    and asking for a new link cancels the older ones.
 *  - At most 3 links per account per hour, and 10 requests per internet address per hour.
 *  - After the reset every session of the account is signed out, and the person gets an email saying so
 *    (if it was not them, they know at once).
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration VALID_FOR = Duration.ofMinutes(30);
    private static final int MAX_PER_ACCOUNT_PER_HOUR = 3;
    private static final int MAX_PER_ADDRESS_PER_HOUR = 10;

    private final PasswordResetTokenRepository tokens;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final EmailService email;
    private final AuditService audit;
    private final Map<String, Deque<Instant>> byAddress = new ConcurrentHashMap<>();

    @Value("${app.public-url:http://localhost:4200}")
    private String publicUrl;

    public PasswordResetService(PasswordResetTokenRepository tokens, UserRepository users, PasswordEncoder passwordEncoder,
                                EmailService email, AuditService audit) {
        this.tokens = tokens;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.audit = audit;
    }

    @Transactional
    public void request(String rawEmail, String ip) {
        String address = rawEmail == null ? "" : rawEmail.trim();
        if (address.isEmpty() || address.length() > 120 || !address.contains("@")) {
            throw new IllegalArgumentException("Enter the email address of your account.");
        }
        if (!allowedFrom(ip)) {
            throw new IllegalStateException("Too many requests from this connection. Please wait an hour and try again.");
        }
        User user = users.findByEmail(address).orElse(null);
        if (user == null || !user.isActive()) {
            log.info("Password reset asked for an unknown or switched-off account");
            return; // same answer as for a real account
        }
        Instant now = Instant.now();
        if (tokens.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(Duration.ofHours(1))) >= MAX_PER_ACCOUNT_PER_HOUR) {
            log.info("Password reset limit reached for user {}", user.getId());
            return; // the earlier emails are still valid; no need to say so
        }
        // older links stop working
        for (PasswordResetToken old : tokens.findByUserIdAndUsedAtIsNull(user.getId())) {
            old.setUsedAt(now);
            tokens.save(old);
        }

        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);

        PasswordResetToken t = new PasswordResetToken();
        t.setUserId(user.getId());
        t.setTokenHash(hash(token));
        t.setCreatedAt(now);
        t.setExpiresAt(now.plus(VALID_FOR));
        t.setRequestIp(ip == null ? null : ip.substring(0, Math.min(64, ip.length())));
        tokens.save(t);

        String link = trimSlash(publicUrl) + "/reset-password?token=" + token;
        email.sendEmail(user.getEmail(), "Choose a new password for DK/Phar",
                "Hello " + user.getName() + ",\n\n"
                        + "Someone (hopefully you) asked to choose a new password for your DK/Phar account.\n\n"
                        + "Open this link within 30 minutes to choose it:\n" + link + "\n\n"
                        + "If you did not ask for this, ignore this email: your password stays the same.\n\n"
                        + "DK/Phar");
    }

    public boolean isValid(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            return false;
        }
        return tokens.findByTokenHash(hash(token.trim()))
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(Instant.now()))
                .isPresent();
    }

    @Transactional
    public void reset(String token, String newPassword, int minLength) {
        String password = newPassword == null ? "" : newPassword;
        if (password.length() < minLength) {
            throw new IllegalArgumentException("Use at least " + minLength + " characters for the new password.");
        }
        if (password.length() > 200) {
            throw new IllegalArgumentException("That password is too long.");
        }
        String clean = token == null ? "" : token.trim();
        PasswordResetToken t = clean.isEmpty() || clean.length() > 100 ? null
                : tokens.findByTokenHashForUpdate(hash(clean)).orElse(null);
        Instant now = Instant.now();
        if (t == null || t.getUsedAt() != null || !t.getExpiresAt().isAfter(now)) {
            throw new IllegalStateException("This link has expired or was already used. Ask for a new one.");
        }
        User user = users.findById(t.getUserId()).filter(User::isActive)
                .orElseThrow(() -> new IllegalStateException("This account cannot be used. Please contact the shop."));

        user.setPassword(passwordEncoder.encode(password));
        user.setPasswordChangedAt(now); // every session that signed in before now is signed out
        users.save(user);
        t.setUsedAt(now);
        tokens.save(t);
        audit.record("PASSWORD_RESET_BY_EMAIL", user.getEmail(), null);

        email.sendEmail(user.getEmail(), "Your DK/Phar password was changed",
                "Hello " + user.getName() + ",\n\nYour password was just changed with a reset link, and you were signed out everywhere.\n\n"
                        + "If this was not you, contact us straight away.\n\nDK/Phar");
    }

    /** Every night: old links are deleted (used or not). */
    @Scheduled(cron = "0 40 3 * * *")
    @Transactional
    public void cleanUp() {
        tokens.deleteOlderThan(Instant.now().minus(Duration.ofDays(1)));
        Instant hourAgo = Instant.now().minus(Duration.ofHours(1));
        byAddress.values().forEach(times -> {
            synchronized (times) {
                times.removeIf(t -> t.isBefore(hourAgo));
            }
        });
        byAddress.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    private boolean allowedFrom(String ip) {
        if (ip == null || ip.isBlank()) {
            return true;
        }
        Instant now = Instant.now();
        Deque<Instant> times = byAddress.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) {
                times.pollFirst();
            }
            if (times.size() >= MAX_PER_ADDRESS_PER_HOUR) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String trimSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
