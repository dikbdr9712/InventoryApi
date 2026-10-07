package com.api.inventory.service;

import com.api.inventory.entity.User;
import com.api.inventory.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * An admin corrects a person's name, sign-in email or phone number (People & access).
 *
 *  - The email and the phone must not belong to another account.
 *  - Everything the person OWNS follows a new email: their orders and payment attempts, notifications, reviews and
 *    ratings, accepted agreements, their customer record, their cash drawers and the packages they pack or deliver.
 *    What they DID in the past keeps the email of that time (activity log, "approved by", "replied by", ...);
 *    the change itself is in the activity log (old -> new), so it can always be traced.
 *  - A new email signs the person out everywhere (their sign-ins were made with the old one): they sign in again
 *    with the new email and the same password. The old and the new address are both told.
 *  - A new phone is also the phone of their customer record (counter sales are matched by phone).
 */
@Service
public class AccountDetailsService {

    /** Tables and columns that say "this belongs to that person", by email. */
    private static final String[][] OWNED_BY_EMAIL = {
            {"orders", "customer_email"},
            {"payment_intents", "customer_email"},
            {"notifications", "user_email"},
            {"product_reviews", "user_email"},
            {"order_feedback", "user_email"},
            {"terms_acceptances", "user_email"},
            {"pos_shifts", "cashier_email"},
            {"order_packages", "packer_email"},
            {"order_packages", "courier_email"}
    };

    @PersistenceContext
    private EntityManager em;

    private final UserRepository users;
    private final AuditService audit;
    private final EmailService email;

    public AccountDetailsService(UserRepository users, AuditService audit, EmailService email) {
        this.users = users;
        this.audit = audit;
        this.email = email;
    }

    public record Change(String name, String email, String phone) {
    }

    /** Returns what changed, in words ("email", "phone", "name"); empty when nothing did. */
    @Transactional
    public List<String> change(User user, Change in) {
        if (in == null) {
            throw new IllegalArgumentException("Fill in the name, email and phone.");
        }
        String name = in.name() == null ? "" : in.name().trim();
        String newEmail = in.email() == null ? "" : in.email().trim();
        String newPhone = in.phone() == null ? "" : in.phone().trim();
        if (name.isEmpty() || name.length() > 100) {
            throw new IllegalArgumentException("Enter the full name (up to 100 characters).");
        }
        if (newEmail.length() > 150 || !newEmail.matches("^\\S+@\\S+\\.\\S+$")) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        if (!newPhone.matches("^[0-9]{8}$")) {
            throw new IllegalArgumentException("Enter an 8-digit phone number.");
        }

        String oldEmail = user.getEmail();
        String oldPhone = user.getPhone();
        boolean emailChanged = !newEmail.equals(oldEmail);
        boolean onlyCase = emailChanged && newEmail.equalsIgnoreCase(oldEmail); // "Karma@x.bt" -> "karma@x.bt": same person
        boolean phoneChanged = !newPhone.equals(oldPhone);
        boolean nameChanged = !name.equals(user.getName());

        if (emailChanged && !onlyCase && otherUserWithEmail(newEmail, user.getId())) {
            throw new IllegalStateException("This email belongs to another account.");
        }
        if (phoneChanged && users.findByPhone(newPhone).filter(u -> !u.getId().equals(user.getId())).isPresent()) {
            throw new IllegalStateException("This phone number belongs to another account.");
        }

        List<String> changed = new ArrayList<>();
        if (nameChanged) {
            user.setName(name);
            changed.add("name");
        }
        if (phoneChanged) {
            user.setPhone(newPhone);
            em.createNativeQuery("UPDATE customers SET phone = ?1 WHERE user_id = ?2")
                    .setParameter(1, newPhone).setParameter(2, user.getId()).executeUpdate();
            changed.add("phone");
        }
        if (emailChanged) {
            user.setEmail(newEmail);
            for (String[] t : OWNED_BY_EMAIL) {
                em.createNativeQuery("UPDATE " + t[0] + " SET " + t[1] + " = ?1 WHERE LOWER(" + t[1] + ") = LOWER(?2)")
                        .setParameter(1, newEmail).setParameter(2, oldEmail).executeUpdate();
            }
            em.createNativeQuery("UPDATE customers SET email = ?1 WHERE user_id = ?2")
                    .setParameter(1, newEmail).setParameter(2, user.getId()).executeUpdate();
            changed.add("email");
        }
        if (changed.isEmpty()) {
            return changed;
        }
        users.save(user);

        StringBuilder details = new StringBuilder();
        if (emailChanged) details.append("email ").append(oldEmail).append(" -> ").append(newEmail).append("; ");
        if (phoneChanged) details.append("phone ").append(oldPhone).append(" -> ").append(newPhone).append("; ");
        if (nameChanged) details.append("name changed; ");
        audit.record("USER_DETAILS_CHANGED", newEmail + " (" + user.getName() + ")", details.toString().trim());

        if (emailChanged && !onlyCase) {
            String text = "Hello " + user.getName() + ",\n\n"
                    + "The email you sign in to DP DrukBazaars with was changed by our staff\n"
                    + "from " + oldEmail + "\nto   " + newEmail + "\n\n"
                    + "Sign in with the new email and your usual password. Your orders and everything else stay in your account.\n"
                    + "If you did not ask for this, contact us straight away.\n\nDP DrukBazaars";
            email.sendEmail(oldEmail, "Your DP DrukBazaars sign-in email was changed", text);
            email.sendEmail(newEmail, "Your DP DrukBazaars sign-in email was changed", text);
        }
        return changed;
    }

    /** Case does not matter for emails: "A@x.bt" and "a@x.bt" are the same person. */
    private boolean otherUserWithEmail(String emailAddress, Long userId) {
        Number n = (Number) em.createNativeQuery("SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?1) AND id <> ?2")
                .setParameter(1, emailAddress).setParameter(2, userId).getSingleResult();
        return n.longValue() > 0;
    }
}
