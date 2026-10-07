package com.api.inventory.service;

import com.api.inventory.entity.ItemMaster;
import com.api.inventory.entity.SellerProfile;
import com.api.inventory.entity.SiteText;
import com.api.inventory.entity.TeamMember;
import com.api.inventory.exception.ResourceNotFoundException;
import com.api.inventory.repository.ItemMasterRepository;
import com.api.inventory.repository.OrderRepository;
import com.api.inventory.repository.SellerProfileRepository;
import com.api.inventory.repository.SiteTextRepository;
import com.api.inventory.repository.TeamMemberRepository;
import com.api.inventory.security.CurrentUser;
import com.api.inventory.service.files.FileStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The About page, managed by staff with "site.manage": its texts (introduction, mission, vision), whether the live
 * numbers show, and the people on "Meet the team" (photo, name, role, a short introduction, order, shown or hidden).
 *
 *  - A text that was never changed is not stored: the page shows the wording built in here (DEFAULTS).
 *  - Team photos go to the file store like product photos (so they survive on hosting without a lasting disk):
 *    the type is read from the file itself, at most 5 MB, and a replaced photo of the same person is deleted.
 *  - The live numbers are counted from the shop itself: products customers can see, approved sellers,
 *    delivered orders, and the customers' average rating of the service.
 */
@Service
public class SiteService {

    public static final String INTRO = "about.intro";
    public static final String MISSION = "about.mission";
    public static final String VISION = "about.vision";
    public static final String NUMBERS = "about.numbers";

    public static final Map<String, String> DEFAULTS = Map.of(
            INTRO, "DP DrukBazaars is an online marketplace from Bhutan. We connect people with products they can trust, "
                    + "and with the local sellers and riders who bring them to their door.",
            MISSION, "Our mission is to make online shopping in Bhutan simple, safe and reliable: easy ordering, safe payment "
                    + "from your own bank account, and delivery you can follow from start to finish. We help local businesses "
                    + "and makers reach more customers.",
            VISION, "Our vision is a marketplace where every local business in Bhutan can sell to customers across the "
                    + "country, and every customer can shop with confidence, wherever they live.",
            NUMBERS, "true");

    private static final int MAX_INTRO = 600;
    private static final int MAX_TEXT = 800;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SiteTextRepository texts;
    private final TeamMemberRepository team;
    private final ItemMasterRepository items;
    private final SellerProfileRepository sellers;
    private final OrderRepository orders;
    private final ReviewService reviews;
    private final FileStore store;
    private final AuditService audit;

    public SiteService(SiteTextRepository texts, TeamMemberRepository team, ItemMasterRepository items,
                       SellerProfileRepository sellers, OrderRepository orders, ReviewService reviews,
                       FileStore store, AuditService audit) {
        this.texts = texts;
        this.team = team;
        this.items = items;
        this.sellers = sellers;
        this.orders = orders;
        this.reviews = reviews;
        this.store = store;
        this.audit = audit;
    }

    public record Person(Long id, String name, String role, String bio, String photo, boolean visible, int sortOrder) {
    }

    /** Counted from the shop. A number that is 0 is not shown on the page. */
    public record Numbers(long products, long sellers, long delivered, BigDecimal rating, int ratings) {
    }

    public record About(String intro, String mission, String vision, boolean showNumbers, Numbers numbers, List<Person> team) {
    }

    public record Texts(String intro, String mission, String vision, Boolean showNumbers) {
    }

    /** For the staff page: every person (hidden too), when and by whom the texts last changed, and the original wording. */
    public record AdminAbout(About page, List<Person> team, String updatedBy, Instant updatedAt, Map<String, String> defaults) {
    }

    // ================= For everyone =================

    @Transactional(readOnly = true)
    public About about() {
        Map<String, String> t = currentTexts();
        boolean show = Boolean.parseBoolean(t.get(NUMBERS));
        List<Person> people = team.findByVisibleTrueOrderBySortOrderAscIdAsc().stream().map(SiteService::view).toList();
        return new About(t.get(INTRO), t.get(MISSION), t.get(VISION), show, show ? numbers() : null, people);
    }

    private Numbers numbers() {
        Set<Long> approved = sellers.findAll().stream().filter(SellerProfile::isApproved).map(SellerProfile::getId)
                .collect(Collectors.toSet());
        // the same rule as the product list for shoppers: our own products, and switched-on products of approved sellers
        long products = items.findAll().stream()
                .filter(i -> i.getSellerId() == null || (!Boolean.FALSE.equals(i.getIsActive()) && approved.contains(i.getSellerId())))
                .count();
        ReviewService.ServiceSummary rated = reviews.service();
        return new Numbers(products, approved.size(), orders.countByOrderStatusIgnoreCase("COMPLETED"),
                rated.serviceAverage(), rated.count());
    }

    // ================= Staff =================

    @Transactional(readOnly = true)
    public AdminAbout forStaff() {
        List<SiteText> stored = texts.findAll();
        SiteText last = stored.stream().filter(s -> s.getUpdatedAt() != null)
                .max((a, b) -> a.getUpdatedAt().compareTo(b.getUpdatedAt())).orElse(null);
        Map<String, String> t = currentTexts();
        About page = new About(t.get(INTRO), t.get(MISSION), t.get(VISION), Boolean.parseBoolean(t.get(NUMBERS)), null, List.of());
        List<Person> everyone = team.findAllByOrderBySortOrderAscIdAsc().stream().map(SiteService::view).toList();
        return new AdminAbout(page, everyone, last == null ? null : last.getUpdatedBy(), last == null ? null : last.getUpdatedAt(), DEFAULTS);
    }

    @Transactional
    public AdminAbout saveTexts(Texts in) {
        String intro = required(in.intro(), "the introduction", MAX_INTRO);
        String mission = required(in.mission(), "the mission", MAX_TEXT);
        String vision = required(in.vision(), "the vision", MAX_TEXT);
        Map<String, String> next = new LinkedHashMap<>();
        next.put(INTRO, intro);
        next.put(MISSION, mission);
        next.put(VISION, vision);
        next.put(NUMBERS, String.valueOf(in.showNumbers() == null || in.showNumbers()));
        Map<String, String> before = currentTexts();
        Instant now = Instant.now();
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, String> e : next.entrySet()) {
            if (e.getValue().equals(before.get(e.getKey()))) {
                continue;
            }
            SiteText row = texts.findById(e.getKey()).orElseGet(() -> {
                SiteText s = new SiteText();
                s.setTextKey(e.getKey());
                return s;
            });
            row.setTextValue(e.getValue());
            row.setUpdatedBy(CurrentUser.email());
            row.setUpdatedAt(now);
            texts.save(row);
            changed.add(e.getKey().substring("about.".length()));
        }
        if (!changed.isEmpty()) {
            audit.record("SITE_ABOUT_CHANGED", "About page", String.join(", ", changed));
        }
        return forStaff();
    }

    @Transactional
    public Person addPerson(String name, String role, String bio, Boolean visible, MultipartFile photo) {
        ProductPhotos.check(photo); // before anything is saved
        TeamMember m = new TeamMember();
        fill(m, name, role, bio, visible);
        m.setSortOrder(team.findAll().stream().mapToInt(TeamMember::getSortOrder).max().orElse(0) + 1);
        m.setCreatedAt(Instant.now());
        m = team.save(m);
        m.setPhoto(savePhoto(m.getId(), photo, null));
        team.save(m);
        audit.record("TEAM_MEMBER_ADDED", m.getName(), m.getRole());
        return view(m);
    }

    @Transactional
    public Person updatePerson(Long id, String name, String role, String bio, Boolean visible, MultipartFile photo, boolean removePhoto) {
        TeamMember m = find(id);
        ProductPhotos.check(photo);
        fill(m, name, role, bio, visible);
        if (photo != null && !photo.isEmpty()) {
            m.setPhoto(savePhoto(m.getId(), photo, m.getPhoto()));
        } else if (removePhoto) {
            deleteOwnPhoto(m.getId(), m.getPhoto());
            m.setPhoto(null);
        }
        m.setUpdatedAt(Instant.now());
        team.save(m);
        audit.record("TEAM_MEMBER_CHANGED", m.getName(), m.getRole());
        return view(m);
    }

    @Transactional
    public void removePerson(Long id) {
        TeamMember m = find(id);
        deleteOwnPhoto(m.getId(), m.getPhoto());
        team.delete(m);
        audit.record("TEAM_MEMBER_REMOVED", m.getName(), m.getRole());
    }

    /** The new order: every person's id, first to last. */
    @Transactional
    public List<Person> reorder(List<Long> ids) {
        List<TeamMember> all = team.findAll();
        Set<Long> known = all.stream().map(TeamMember::getId).collect(Collectors.toSet());
        if (ids == null || ids.size() != known.size() || !known.equals(new HashSet<>(ids))) {
            throw new IllegalStateException("The team changed meanwhile. Please reload the page and try again.");
        }
        Map<Long, TeamMember> byId = all.stream().collect(Collectors.toMap(TeamMember::getId, m -> m));
        for (int i = 0; i < ids.size(); i++) {
            TeamMember m = byId.get(ids.get(i));
            m.setSortOrder(i + 1);
            team.save(m);
        }
        return team.findAllByOrderBySortOrderAscIdAsc().stream().map(SiteService::view).toList();
    }

    // ================= Helpers =================

    private Map<String, String> currentTexts() {
        Map<String, String> t = new LinkedHashMap<>(DEFAULTS);
        for (SiteText s : texts.findAll()) {
            if (t.containsKey(s.getTextKey()) && s.getTextValue() != null && !s.getTextValue().isBlank()) {
                t.put(s.getTextKey(), s.getTextValue());
            }
        }
        return t;
    }

    private TeamMember find(Long id) {
        return team.findById(id).orElseThrow(() -> new ResourceNotFoundException("That person is not on the team any more."));
    }

    private static void fill(TeamMember m, String name, String role, String bio, Boolean visible) {
        m.setName(required(name, "the name", 80));
        m.setRole(required(role, "the role", 80));
        String b = bio == null ? "" : bio.trim();
        if (b.length() > 400) {
            throw new IllegalArgumentException("Keep the introduction under 400 characters.");
        }
        m.setBio(b.isEmpty() ? null : b);
        m.setVisible(visible == null || visible);
    }

    private static String required(String value, String what, int max) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException("Fill in " + what + ".");
        }
        if (v.length() > max) {
            throw new IllegalArgumentException("Keep " + what + " under " + max + " characters.");
        }
        return v;
    }

    private String savePhoto(Long id, MultipartFile photo, String previous) {
        if (photo == null || photo.isEmpty()) {
            return previous;
        }
        String ext = ProductPhotos.extensionOf(photo);
        byte[] random = new byte[4];
        RANDOM.nextBytes(random);
        String name = "team-" + id + "-" + java.util.HexFormat.of().formatHex(random) + "." + ext;
        try {
            store.put(FileStore.PUBLIC, name, photo.getBytes(), FileStore.typeOf(name));
        } catch (IOException e) {
            throw new IllegalStateException("The photo could not be saved. Please try again.");
        }
        deleteOwnPhoto(id, previous);
        return "/uploads/" + name;
    }

    /** Only a photo uploaded for this person (never a picture of the website itself). */
    private void deleteOwnPhoto(Long id, String path) {
        if (path == null || !path.startsWith("/uploads/team-" + id + "-")) {
            return;
        }
        String file = path.substring("/uploads/".length());
        if (file.contains("/") || file.contains("\\") || file.contains("..")) {
            return;
        }
        store.delete(FileStore.PUBLIC, file);
    }

    private static Person view(TeamMember m) {
        return new Person(m.getId(), m.getName(), m.getRole(), m.getBio(), m.getPhoto(), m.isVisible(), m.getSortOrder());
    }
}
