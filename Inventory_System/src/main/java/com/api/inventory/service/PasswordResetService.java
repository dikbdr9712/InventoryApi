package com.api.inventory.service;

import com.api.inventory.entity.PasswordResetCode;
import com.api.inventory.entity.PasswordResetToken;
import com.api.inventory.entity.User;
import com.api.inventory.repository.PasswordResetCodeRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Forgot password", done by the person themselves:
 *   1. They choose email or text message and type the email or phone number of their account.
 *   2. We send a 6-digit code there (the email also holds a link that skips step 3).
 *   3. The right code gives a one-time ticket, and with it they choose a new password.
 *
 *  - The answers never say whether an email or phone number has an account (nobody can test which are registered).
 *  - A code works for 10 minutes and for 5 tries; only its BCrypt hash is stored. A new code cancels the older ones.
 *    At most one code a minute and 3 an hour per account and way (email / SMS), and 10 requests and 30 tries
 *    per internet address per hour.
 *  - The link holds 32 random bytes; only their SHA-256 fingerprint is stored. It works once, for 30 minutes.
 *    The ticket after a right code is the same kind of secret and works once, for 15 minutes.
 *  - After the reset every session of the account is signed out, every other code and link stops working, and the
 *    person gets an email saying so (if it was not them, they know at once).
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration LINK_VALID_FOR = Duration.ofMinutes(30);
    private static final Duration CODE_VALID_FOR = Duration.ofMinutes(10);
    private static final Duration TICKET_VALID_FOR = Duration.ofMinutes(15);
    /** Seconds before the same account can be sent another code the same way. */
    public static final int RESEND_AFTER_SECONDS = 60;
    private static final int MAX_CODES_PER_HOUR = 3;
    private static final int MAX_TRIES_PER_CODE = 5;
    private static final int MAX_REQUESTS_PER_ADDRESS_PER_HOUR = 10;
    private static final int MAX_TRIES_PER_ADDRESS_PER_HOUR = 30;
    private static final String WRONG_CODE =
            "That code is not right, or it has expired. Check the latest message we sent, or ask for a new code.";

    private final PasswordResetTokenRepository tokens;
    private final PasswordResetCodeRepository codes;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final EmailService email;
    private final SmsService sms;
    private final AuditService audit;
    private final Map<String, Deque<Instant>> requestsByAddress = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> triesByAddress = new ConcurrentHashMap<>();
    /** Checked when there is no code to check, so a wrong guess takes as long whether or not the account exists. */
    private volatile String decoyHash;

    @Value("${app.public-url:http://localhost:4200}")
    private String publicUrl;

    public PasswordResetService(PasswordResetTokenRepository tokens, PasswordResetCodeRepository codes, UserRepository users,
                                PasswordEncoder passwordEncoder, EmailService email, SmsService sms, AuditService audit) {
        this.tokens = tokens;
        this.codes = codes;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.sms = sms;
        this.audit = audit;
    }

    /** Which ways can reach people now. A way that cannot is not offered (the page then says to ask the shop). */
    public record Ways(boolean email, boolean sms) {}

    public Ways ways() {
        return new Ways(email.isAvailable(), sms.isAvailable());
    }

    /** EMAIL or SMS from what the page sends ("email", "sms", "phone"). */
    public static String channel(String method) {
        String m = method == null || method.isBlank() ? "email" : method.trim().toLowerCase();
        return switch (m) {
            case "email" -> PasswordResetCode.EMAIL;
            case "sms", "phone" -> PasswordResetCode.SMS;
            default -> throw new IllegalArgumentException("Choose email or text message.");
        };
    }

    // ================= Step 1: send a code =================

    @Transactional
    public void sendCode(String method, String rawEmail, String rawPhone, String ip) {
        String channel = channel(method);
        boolean byEmail = PasswordResetCode.EMAIL.equals(channel);
        String address = byEmail ? cleanEmail(rawEmail) : cleanPhone(rawPhone);
        if (byEmail ? !email.isAvailable() : !sms.isAvailable()) {
            throw new IllegalStateException(byEmail
                    ? "We cannot send emails yet. Use your phone number, or call the shop."
                    : "We cannot send text messages yet. Use your email, or call the shop.");
        }
        if (!allowed(requestsByAddress, ip, MAX_REQUESTS_PER_ADDRESS_PER_HOUR)) {
            throw new IllegalStateException("Too many requests from this connection. Please wait an hour and try again.");
        }
        User user = find(channel, address).filter(User::isActive).orElse(null);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String codeHash = passwordEncoder.encode(code); // also for unknown accounts, so both answers take as long
        if (user == null) {
            log.info("Password reset code asked for an unknown or switched-off account ({})", channel);
            return; // same answer as for a real account
        }
        Instant now = Instant.now();
        Optional<PasswordResetCode> last = codes.findFirstByUserIdAndChannelOrderByCreatedAtDesc(user.getId(), channel);
        if (last.isPresent() && last.get().getCreatedAt().isAfter(now.minusSeconds(RESEND_AFTER_SECONDS))) {
            return; // the code from a moment ago is still on its way
        }
        if (codes.countByUserIdAndChannelAndCreatedAtAfter(user.getId(), channel, now.minus(Duration.ofHours(1))) >= MAX_CODES_PER_HOUR) {
            log.info("Password reset code limit reached for user {} ({})", user.getId(), channel);
            return; // the latest code still works; no need to say so
        }
        // only the newest code works
        for (PasswordResetCode old : codes.findByUserIdAndUsedAtIsNull(user.getId())) {
            old.setUsedAt(now);
            codes.save(old);
        }
        PasswordResetCode c = new PasswordResetCode();
        c.setUserId(user.getId());
        c.setChannel(channel);
        c.setCodeHash(codeHash);
        c.setAttempts(0);
        c.setCreatedAt(now);
        c.setExpiresAt(now.plus(CODE_VALID_FOR));
        c.setRequestIp(shortIp(ip));
        codes.save(c);

        if (byEmail) {
            String link = trimSlash(publicUrl) + "/reset-password?token=" + newLink(user, now, ip);
            email.sendEmail(user.getEmail(), code + " is your DP DrukBazaars code to choose a new password",
                    "Hello " + user.getName() + ",\n\n"
                            + "Someone (hopefully you) asked to choose a new password for your DP DrukBazaars account.\n\n"
                            + "Your code: " + code + "\n"
                            + "Type it on the page where you asked for it. It works for 10 minutes.\n\n"
                            + "Or open this link within 30 minutes to choose the new password:\n" + link + "\n\n"
                            + "Never share this code or link. DP DrukBazaars staff will never ask you for them.\n"
                            + "If you did not ask for this, ignore this email: your password stays the same.\n\n"
                            + "DP DrukBazaars");
        } else {
            sms.send(user.getPhone(), "DP DrukBazaars: " + code + " is your code to choose a new password. It works for 10 minutes."
                    + " Never share it, we will never ask for it.");
        }
    }

    // ================= Step 2: check the code =================

    /** The right code gives a one-time ticket (15 minutes) to choose the new password. */
    @Transactional(noRollbackFor = IllegalArgumentException.class) // a wrong try is counted
    public String verifyCode(String method, String rawEmail, String rawPhone, String rawCode, String ip) {
        String channel = channel(method);
        String address = PasswordResetCode.EMAIL.equals(channel) ? cleanEmail(rawEmail) : cleanPhone(rawPhone);
        String code = rawCode == null ? "" : rawCode.replaceAll("\\s", "");
        if (!code.matches("\\d{6}")) {
            throw new IllegalArgumentException("Enter the 6-digit code.");
        }
        if (!allowed(triesByAddress, ip, MAX_TRIES_PER_ADDRESS_PER_HOUR)) {
            throw new IllegalStateException("Too many tries from this connection. Please wait an hour and try again.");
        }
        Instant now = Instant.now();
        User user = find(channel, address).filter(User::isActive).orElse(null);
        List<PasswordResetCode> working = user == null ? List.of() : codes.findWorkingForUpdate(user.getId(), channel, now);
        if (working.isEmpty()) {
            passwordEncoder.matches(code, decoy());
            throw new IllegalArgumentException(WRONG_CODE);
        }
        PasswordResetCode c = working.get(0);
        if (!passwordEncoder.matches(code, c.getCodeHash())) {
            c.setAttempts(c.getAttempts() + 1);
            if (c.getAttempts() >= MAX_TRIES_PER_CODE) {
                c.setUsedAt(now); // tried too often: a new code is needed
            }
            codes.save(c);
            throw new IllegalArgumentException(WRONG_CODE);
        }
        c.setUsedAt(now);
        codes.save(c);
        audit.record("PASSWORD_RESET_CODE_OK", user.getEmail(), channel);
        return newTicket(user, now, ip);
    }

    // ================= Step 3: the new password =================

    public boolean isValid(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            return false;
        }
        return tokens.findByTokenHash(hash(token.trim()))
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(Instant.now()))
                .isPresent();
    }

    /** Sets the new password with a link or a ticket. Gives back the account's email, to sign in with. */
    @Transactional
    public String reset(String token, String newPassword, int minLength) {
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
            throw new IllegalStateException("This link or code has expired or was already used. Ask for a new one.");
        }
        User user = users.findById(t.getUserId()).filter(User::isActive)
                .orElseThrow(() -> new IllegalStateException("This account cannot be used. Please contact the shop."));

        user.setPassword(passwordEncoder.encode(password));
        user.setPasswordChangedAt(now); // every session that signed in before now is signed out
        users.save(user);
        t.setUsedAt(now);
        tokens.save(t);
        // every other link, ticket and code of the account stops working
        for (PasswordResetToken other : tokens.findByUserIdAndUsedAtIsNull(user.getId())) {
            other.setUsedAt(now);
            tokens.save(other);
        }
        for (PasswordResetCode other : codes.findByUserIdAndUsedAtIsNull(user.getId())) {
            other.setUsedAt(now);
            codes.save(other);
        }
        audit.record("PASSWORD_RESET_SELF", user.getEmail(), null);

        email.sendEmail(user.getEmail(), "Your DP DrukBazaars password was changed",
                "Hello " + user.getName() + ",\n\nYour password was just changed with a reset code or link, and you were signed out everywhere.\n\n"
                        + "If this was not you, contact us straight away.\n\nDP DrukBazaars");
        return user.getEmail();
    }

    /** Every night: old links, tickets and codes are deleted (used or not). */
    @Scheduled(cron = "0 40 3 * * *")
    @Transactional
    public void cleanUp() {
        Instant dayAgo = Instant.now().minus(Duration.ofDays(1));
        tokens.deleteOlderThan(dayAgo);
        codes.deleteOlderThan(dayAgo);
        Instant hourAgo = Instant.now().minus(Duration.ofHours(1));
        for (Map<String, Deque<Instant>> byAddress : List.of(requestsByAddress, triesByAddress)) {
            byAddress.values().forEach(times -> {
                synchronized (times) {
                    times.removeIf(t -> t.isBefore(hourAgo));
                }
            });
            byAddress.entrySet().removeIf(e -> e.getValue().isEmpty());
        }
    }

    // ================= Helpers =================

    private Optional<User> find(String channel, String address) {
        if (PasswordResetCode.EMAIL.equals(channel)) {
            return users.findByEmail(address);
        }
        return users.findByPhone(address).or(() -> users.findByPhone("975" + address));
    }

    /** The email link: the older links and tickets stop working. */
    private String newLink(User user, Instant now, String ip) {
        for (PasswordResetToken old : tokens.findByUserIdAndUsedAtIsNull(user.getId())) {
            old.setUsedAt(now);
            tokens.save(old);
        }
        return newSecret(user, now, LINK_VALID_FOR, ip);
    }

    private String newTicket(User user, Instant now, String ip) {
        return newSecret(user, now, TICKET_VALID_FOR, ip);
    }

    private String newSecret(User user, Instant now, Duration validFor, String ip) {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        PasswordResetToken t = new PasswordResetToken();
        t.setUserId(user.getId());
        t.setTokenHash(hash(token));
        t.setCreatedAt(now);
        t.setExpiresAt(now.plus(validFor));
        t.setRequestIp(shortIp(ip));
        tokens.save(t);
        return token;
    }

    private String decoy() {
        if (decoyHash == null) {
            decoyHash = passwordEncoder.encode("no-code-" + RANDOM.nextInt());
        }
        return decoyHash;
    }

    private static String cleanEmail(String raw) {
        String address = raw == null ? "" : raw.trim();
        if (address.isEmpty() || address.length() > 120 || !address.contains("@")) {
            throw new IllegalArgumentException("Enter the email address of your account.");
        }
        return address;
    }

    /** The 8 digits of a Bhutan number, also when typed with +975, spaces or dashes. */
    static String cleanPhone(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        if (digits.length() == 11 && digits.startsWith("975")) {
            digits = digits.substring(3);
        }
        if (digits.length() != 8) {
            throw new IllegalArgumentException("Enter the 8-digit phone number of your account.");
        }
        return digits;
    }

    private static boolean allowed(Map<String, Deque<Instant>> byAddress, String ip, int max) {
        if (ip == null || ip.isBlank()) {
            return true;
        }
        Instant now = Instant.now();
        Deque<Instant> times = byAddress.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) {
                times.pollFirst();
            }
            if (times.size() >= max) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }

    private static String shortIp(String ip) {
        return ip == null ? null : ip.substring(0, Math.min(64, ip.length()));
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
