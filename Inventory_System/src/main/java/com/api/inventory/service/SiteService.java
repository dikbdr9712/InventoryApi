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

    /**
     * The shop's contact details and links, shown in the footer, on Contact, About and Forgot password, and printed
     * on receipts, invoices and credit notes. A stored value wins even when empty (an empty link = no icon).
     */
    public static final String PHONE = "shop.phone";
    /** More numbers for the Contact page, comma-separated (optional). */
    public static final String OTHER_PHONES = "shop.phones";
    public static final String EMAIL = "shop.email";
    public static final String ADDRESS = "shop.address";
    public static final String FACEBOOK = "link.facebook";
    public static final String INSTAGRAM = "link.instagram";
    public static final String YOUTUBE = "link.youtube";
    public static final String TIKTOK = "link.tiktok";
    public static final Map<String, String> SHOP_DEFAULTS = Map.of(
            PHONE, "77269712",
            OTHER_PHONES, "17941806, 77803506",
            EMAIL, "dpdrukbazaars@gmail.com",
            ADDRESS, "Thimphu, Bhutan",
            FACEBOOK, "https://www.facebook.com/pharmith.lepcha.2025",
            INSTAGRAM, "",
            YOUTUBE, "",
            TIKTOK, "");

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

    /** The shop's contact details and its pages elsewhere (empty link = not shown). */
    public record ShopDetails(String phone, String otherPhones, String email, String address, String facebook,
                              String instagram, String youtube, String tiktok) {
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

    // ================= Contact details and links =================

    @Transactional(readOnly = true)
    public ShopDetails shopDetails() {
        Map<String, String> v = new LinkedHashMap<>(SHOP_DEFAULTS);
        for (SiteText s : texts.findAllById(SHOP_DEFAULTS.keySet())) {
            v.put(s.getTextKey(), s.getTextValue() == null ? "" : s.getTextValue());
        }
        return new ShopDetails(v.get(PHONE), v.get(OTHER_PHONES), v.get(EMAIL), v.get(ADDRESS), v.get(FACEBOOK), v.get(INSTAGRAM),
                v.get(YOUTUBE), v.get(TIKTOK));
    }

    @Transactional
    public ShopDetails saveShopDetails(ShopDetails in) {
        if (in == null) {
            throw new IllegalArgumentException("Fill in the shop's details.");
        }
        String phone = clean(in.phone());
        if (!phone.matches("\\+?[0-9 ]{7,20}") || phone.replaceAll("\\D", "").length() < 7) {
            throw new IllegalArgumentException("Enter the shop's phone number (digits only, for example 77269712).");
        }
        List<String> others = new ArrayList<>();
        for (String n : clean(in.otherPhones()).split(",")) {
            String number = n.trim();
            if (number.isEmpty()) {
                continue;
            }
            if (!number.matches("\\+?[0-9 ]{7,20}") || number.replaceAll("\\D", "").length() < 7) {
                throw new IllegalArgumentException("\"" + number + "\" is not a phone number. Separate more numbers with commas.");
            }
            others.add(number);
        }
        if (others.size() > 5) {
            throw new IllegalArgumentException("At most 5 more phone numbers.");
        }
        String email = clean(in.email());
        if (email.length() > 120 || !email.matches("^\\S+@\\S+\\.\\S+$")) {
            throw new IllegalArgumentException("Enter the shop's email address.");
        }
        String address = required(in.address(), "the address", 200);
        Map<String, String> next = new LinkedHashMap<>();
        next.put(PHONE, phone);
        next.put(OTHER_PHONES, String.join(", ", others));
        next.put(EMAIL, email);
        next.put(ADDRESS, address);
        next.put(FACEBOOK, link(in.facebook(), "Facebook"));
        next.put(INSTAGRAM, link(in.instagram(), "Instagram"));
        next.put(YOUTUBE, link(in.youtube(), "YouTube"));
        next.put(TIKTOK, link(in.tiktok(), "TikTok"));

        ShopDetails before = shopDetails();
        Map<String, String> old = Map.of(PHONE, before.phone(), OTHER_PHONES, before.otherPhones(), EMAIL, before.email(), ADDRESS, before.address(),
                FACEBOOK, before.facebook(), INSTAGRAM, before.instagram(), YOUTUBE, before.youtube(), TIKTOK, before.tiktok());
        Instant now = Instant.now();
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, String> e : next.entrySet()) {
            if (e.getValue().equals(old.get(e.getKey()))) {
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
            changed.add(e.getKey().substring(e.getKey().indexOf('.') + 1));
        }
        if (!changed.isEmpty()) {
            audit.record("SITE_DETAILS_CHANGED", "Contact details and links", String.join(", ", changed));
        }
        return shopDetails();
    }

    /** Empty, or a full https address. */
    private static String link(String value, String what) {
        String v = clean(value);
        if (v.isEmpty()) {
            return "";
        }
        if (v.length() > 300 || !v.matches("https://[^\\s<>\"]+")) {
            throw new IllegalArgumentException("The " + what + " link must be a full address starting with https://, or empty.");
        }
        return v;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
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
