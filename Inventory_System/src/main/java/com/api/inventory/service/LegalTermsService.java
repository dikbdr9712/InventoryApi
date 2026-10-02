package com.api.inventory.service;

import com.api.inventory.entity.LegalTerms;
import com.api.inventory.entity.TermsAcceptance;
import com.api.inventory.entity.User;
import com.api.inventory.exception.TermsNotAcceptedException;
import com.api.inventory.repository.LegalTermsRepository;
import com.api.inventory.repository.TermsAcceptanceRepository;
import com.api.inventory.repository.UserRepository;
import com.api.inventory.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The agreements people accept (Seller Agreement, Driver Agreement, customers' Terms of Use and Privacy),
 * their versions, and the proof of who accepted which version.
 *
 * On first start, version 1 of each is loaded from src/main/resources/legal/*.txt. After that, an admin
 * changes the wording by publishing a new version (Marketplace > Terms). Old versions are kept unchanged.
 */
@Service
public class LegalTermsService implements ApplicationRunner {

    public static final Set<String> TYPES = Set.of(LegalTerms.SELLER, LegalTerms.RIDER, LegalTerms.CUSTOMER);

    private static final Map<String, String[]> DEFAULTS = Map.of(
            LegalTerms.SELLER, new String[] {"Seller Agreement", "legal/seller-agreement.txt"},
            LegalTerms.RIDER, new String[] {"Delivery Driver Agreement", "legal/driver-agreement.txt"},
            LegalTerms.CUSTOMER, new String[] {"Terms of Use and Privacy", "legal/customer-terms.txt"});

    private static final int MAX_BODY = 60_000;

    private final LegalTermsRepository terms;
    private final TermsAcceptanceRepository acceptances;
    private final UserRepository users;
    private final AuditService audit;

    public LegalTermsService(LegalTermsRepository terms, TermsAcceptanceRepository acceptances, UserRepository users, AuditService audit) {
        this.terms = terms;
        this.acceptances = acceptances;
        this.users = users;
        this.audit = audit;
    }

    public record TermsView(String type, Integer version, String title, String body, String changeSummary,
                            Instant publishedAt, String publishedBy, long acceptedCount) {
    }

    public record TermsStatus(String type, Integer currentVersion, Integer acceptedVersion, boolean accepted, Instant acceptedAt,
                              String changeSummary) {
    }

    public record PublishRequest(String title, String body, String changeSummary) {
    }

    // ---------- setup ----------

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Map.Entry<String, String[]> d : DEFAULTS.entrySet()) {
            if (terms.findFirstByTermsTypeOrderByVersionDesc(d.getKey()).isEmpty()) {
                LegalTerms t = new LegalTerms();
                t.setTermsType(d.getKey());
                t.setVersion(1);
                t.setTitle(d.getValue()[0]);
                t.setBody(readResource(d.getValue()[1]));
                t.setChangeSummary("First version.");
                t.setPublishedAt(Instant.now());
                t.setPublishedBy("system");
                terms.save(t);
            }
        }
    }

    private static String readResource(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "# Agreement\n\nThe text of this agreement is being prepared. Please contact us before accepting.";
        }
    }

    // ---------- reading ----------

    public LegalTerms current(String type) {
        String t = type(type);
        return terms.findFirstByTermsTypeOrderByVersionDesc(t)
                .orElseThrow(() -> new IllegalStateException("These terms are not available yet."));
    }

    public TermsView view(LegalTerms t) {
        return new TermsView(t.getTermsType(), t.getVersion(), t.getTitle(), t.getBody(), t.getChangeSummary(),
                t.getPublishedAt(), t.getPublishedBy(), acceptances.countByTermsTypeAndVersion(t.getTermsType(), t.getVersion()));
    }

    public TermsView currentView(String type) {
        return view(current(type));
    }

    public Optional<TermsView> version(String type, int version) {
        return terms.findByTermsTypeAndVersion(type(type), version).map(this::view);
    }

    public List<TermsView> history(String type) {
        return terms.findByTermsTypeOrderByVersionDesc(type(type)).stream().map(this::view).toList();
    }

    public TermsStatus status(String email, String type) {
        LegalTerms cur = current(type);
        Optional<TermsAcceptance> last = acceptances.findFirstByUserEmailIgnoreCaseAndTermsTypeOrderByVersionDescIdDesc(email, cur.getTermsType());
        Integer accepted = last.map(TermsAcceptance::getVersion).orElse(null);
        return new TermsStatus(cur.getTermsType(), cur.getVersion(), accepted, accepted != null && accepted >= cur.getVersion(),
                last.map(TermsAcceptance::getAcceptedAt).orElse(null), cur.getChangeSummary());
    }

    public boolean hasAcceptedCurrent(String email, String type) {
        return email != null && status(email, type).accepted();
    }

    /** For the seller and driver dashboards: refuse with a clear "please accept the new terms" answer. */
    public void requireAcceptedCurrent(String email, String type) {
        if (!hasAcceptedCurrent(email, type)) {
            LegalTerms cur = current(type);
            throw new TermsNotAcceptedException(cur.getTermsType(), cur.getVersion(), cur.getTitle());
        }
    }

    // ---------- accepting ----------

    /**
     * Records that this person accepts this version. The version must be the current one: a screen that was
     * open while the admin published a new version cannot accept the old text by mistake.
     */
    @Transactional
    public TermsAcceptance accept(String email, String type, Integer version, HttpServletRequest request) {
        LegalTerms cur = current(type);
        if (version == null || !version.equals(cur.getVersion())) {
            throw new IllegalStateException("The " + cur.getTitle() + " has changed. Please read the latest version (version "
                    + cur.getVersion() + ") and accept it.");
        }
        User user = users.findByEmail(email).orElseThrow(() -> new IllegalStateException("Please sign in again."));
        TermsAcceptance a = new TermsAcceptance();
        a.setUserId(user.getId());
        a.setUserEmail(user.getEmail());
        a.setTermsType(cur.getTermsType());
        a.setVersion(cur.getVersion());
        a.setAcceptedAt(Instant.now());
        if (request != null) {
            a.setIpAddress(clientIp(request));
            String agent = request.getHeader("User-Agent");
            a.setUserAgent(agent == null ? null : agent.substring(0, Math.min(300, agent.length())));
        }
        return acceptances.save(a);
    }

    // ---------- publishing (admin) ----------

    @Transactional
    public TermsView publish(String type, PublishRequest request) {
        LegalTerms cur = current(type);
        String title = request.title() == null || request.title().isBlank() ? cur.getTitle() : request.title().trim();
        String body = request.body() == null ? "" : request.body().replace("\r\n", "\n").trim();
        String summary = request.changeSummary() == null ? "" : request.changeSummary().trim();
        if (body.length() < 200) {
            throw new IllegalStateException("The agreement text looks too short. Paste the full text.");
        }
        if (body.length() > MAX_BODY) {
            throw new IllegalStateException("The agreement text is too long (at most " + MAX_BODY + " characters).");
        }
        if (summary.isEmpty()) {
            throw new IllegalStateException("Write a short summary of what changed. People who must accept again will see it.");
        }
        if (body.equals(cur.getBody()) && title.equals(cur.getTitle())) {
            throw new IllegalStateException("Nothing changed compared with version " + cur.getVersion() + ".");
        }
        LegalTerms next = new LegalTerms();
        next.setTermsType(cur.getTermsType());
        next.setVersion(cur.getVersion() + 1);
        next.setTitle(title.substring(0, Math.min(150, title.length())));
        next.setBody(body);
        next.setChangeSummary(summary.substring(0, Math.min(500, summary.length())));
        next.setPublishedAt(Instant.now());
        next.setPublishedBy(CurrentUser.email());
        LegalTerms saved = terms.save(next);
        audit.record("TERMS_PUBLISHED", saved.getTermsType() + " version " + saved.getVersion(), summary);
        return view(saved);
    }

    // ---------- helpers ----------

    public static String type(String raw) {
        String t = raw == null ? "" : raw.trim().toUpperCase();
        if (t.equals("DRIVER")) {
            t = LegalTerms.RIDER;
        }
        if (!TYPES.contains(t)) {
            throw new IllegalArgumentException("Unknown agreement: " + raw);
        }
        return t;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For"); // behind a proxy (the dev server, or nginx when online)
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return ip == null ? null : ip.substring(0, Math.min(64, ip.length()));
    }
}
