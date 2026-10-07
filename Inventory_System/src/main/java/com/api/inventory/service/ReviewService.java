package com.api.inventory.service;

import com.api.inventory.entity.*;
import com.api.inventory.repository.*;
import com.api.inventory.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Ratings and reviews.
 *
 *  - Products: a customer rates a product once it was DELIVERED to them in one of their orders (a verified purchase),
 *    1 to 5 stars and a comment if they like. One review per customer per product; rating again changes it.
 *  - Service: per delivered order, the shop's service and the delivery (1 to 5 stars each) and a comment. The
 *    delivery rating counts for the driver who delivered it.
 *  - Everyone sees the averages and the reviews (with the customer's first name and initial only). Staff
 *    (reviews.manage) can hide an abusive review and reply publicly. A low rating (1 or 2 stars) tells them at once.
 */
@Service
public class ReviewService {

    private static final int MAX_COMMENT = 1000;
    private static final int MAX_REPLY = 500;
    private static final int SHOWN = 50;

    public record ReviewView(Long id, String displayName, int rating, String comment, Instant createdAt, Instant updatedAt,
                             String reply, Instant repliedAt) {
    }

    /** distribution[0] = how many 1-star reviews ... distribution[4] = 5 stars. */
    public record ProductSummary(Long itemId, BigDecimal average, int count, int[] distribution, List<ReviewView> reviews) {
    }

    public record ItemSummary(Long itemId, BigDecimal average, long count) {
    }

    public record FeedbackView(Long id, String displayName, int serviceRating, int deliveryRating, String comment,
                               Instant createdAt, String reply, Instant repliedAt) {
    }

    public record ServiceSummary(BigDecimal serviceAverage, BigDecimal deliveryAverage, int count, int productReviews,
                                 BigDecimal productAverage, List<FeedbackView> reviews) {
    }

    public record RateItem(Long itemId, String name, String imagePath, boolean delivered, Integer myRating, String myComment) {
    }

    public record MyFeedback(int serviceRating, int deliveryRating, String comment) {
    }

    /** What the customer can rate in one of their orders, and what they already rated. */
    public record OrderRating(Long orderId, boolean canRate, String reason, List<RateItem> items, MyFeedback feedback) {
    }

    // ---- staff ----
    public record AdminReview(Long id, Long itemId, String itemName, Long orderId, String userEmail, String displayName,
                              int rating, String comment, boolean hidden, String reply, String repliedBy,
                              Instant createdAt, Instant updatedAt) {
    }

    public record AdminFeedback(Long id, Long orderId, String userEmail, String displayName, int serviceRating, int deliveryRating,
                                String comment, Long riderId, String riderName, boolean hidden, String reply, String repliedBy,
                                Instant createdAt, Instant updatedAt) {
    }

    public record RiderRating(Long riderId, String name, BigDecimal average, long count) {
    }

    public record AdminOverview(List<AdminReview> productReviews, List<AdminFeedback> feedback, List<RiderRating> riders) {
    }

    private final ProductReviewRepository reviews;
    private final OrderFeedbackRepository feedback;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final OrderPackageRepository packages;
    private final ItemMasterRepository items;
    private final UserRepository users;
    private final RiderProfileRepository riders;
    private final NotificationService notify;

    public ReviewService(ProductReviewRepository reviews, OrderFeedbackRepository feedback, OrderRepository orders,
                         OrderItemRepository orderItems, OrderPackageRepository packages, ItemMasterRepository items,
                         UserRepository users, RiderProfileRepository riders, NotificationService notify) {
        this.reviews = reviews;
        this.feedback = feedback;
        this.orders = orders;
        this.orderItems = orderItems;
        this.packages = packages;
        this.items = items;
        this.users = users;
        this.riders = riders;
        this.notify = notify;
    }

    // ================= Everyone =================

    @Transactional(readOnly = true)
    public ProductSummary product(Long itemId) {
        List<ProductReview> list = reviews.findByItemIdAndHiddenFalseOrderByCreatedAtDesc(itemId);
        int[] distribution = new int[5];
        int sum = 0;
        for (ProductReview r : list) {
            distribution[r.getRating() - 1]++;
            sum += r.getRating();
        }
        return new ProductSummary(itemId, average(sum, list.size()), list.size(), distribution,
                list.stream().limit(SHOWN).map(ReviewService::view).toList());
    }

    /** Average and count for every rated product (for the stars on product cards). */
    @Transactional(readOnly = true)
    public List<ItemSummary> summaries() {
        return reviews.summaries().stream()
                .map(row -> new ItemSummary((Long) row[0], BigDecimal.valueOf(((Number) row[1]).doubleValue()).setScale(1, RoundingMode.HALF_UP),
                        ((Number) row[2]).longValue()))
                .toList();
    }

    /** The Customer reviews page: how customers rate our service and delivery, newest comments first. */
    @Transactional(readOnly = true)
    public ServiceSummary service() {
        List<OrderFeedback> list = feedback.findByHiddenFalseOrderByCreatedAtDesc();
        int service = 0, delivery = 0;
        for (OrderFeedback f : list) {
            service += f.getServiceRating();
            delivery += f.getDeliveryRating();
        }
        long productCount = 0;
        double productSum = 0;
        for (Object[] row : reviews.summaries()) {
            long n = ((Number) row[2]).longValue();
            productCount += n;
            productSum += ((Number) row[1]).doubleValue() * n;
        }
        BigDecimal productAverage = productCount == 0 ? null : BigDecimal.valueOf(productSum / productCount).setScale(1, RoundingMode.HALF_UP);
        return new ServiceSummary(average(service, list.size()), average(delivery, list.size()), list.size(), (int) productCount,
                productAverage, list.stream().limit(SHOWN).map(ReviewService::view).toList());
    }

    // ================= The customer =================

    @Transactional(readOnly = true)
    public OrderRating forOrder(Long orderId) {
        Order order = ownOrder(orderId);
        Set<Long> delivered = deliveredItemIds(order);
        List<OrderItem> lines = orderItems.findByOrderId(orderId);
        Set<Long> itemIds = lines.stream().map(OrderItem::getItemId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, ProductReview> mine = reviews.findByUserEmailIgnoreCaseAndItemIdIn(order.getCustomerEmail(), itemIds).stream()
                .collect(Collectors.toMap(ProductReview::getItemId, r -> r));
        List<RateItem> rateItems = new ArrayList<>();
        for (Long itemId : itemIds) {
            ItemMaster item = items.findById(itemId).orElse(null);
            ProductReview r = mine.get(itemId);
            rateItems.add(new RateItem(itemId, item == null ? "Item #" + itemId : item.getItemName(), item == null ? null : item.getImagePath(),
                    delivered.contains(itemId), r == null ? null : r.getRating(), r == null ? null : r.getComment()));
        }
        MyFeedback my = feedback.findByOrderId(orderId)
                .map(f -> new MyFeedback(f.getServiceRating(), f.getDeliveryRating(), f.getComment())).orElse(null);
        boolean can = !delivered.isEmpty();
        return new OrderRating(orderId, can, can ? null : "You can rate this order once it has been delivered.", rateItems, my);
    }

    /** Stars (and a comment) for a product delivered in this order. Rating it again changes the review. */
    @Transactional
    public RateItem rateProduct(Long orderId, Long itemId, Integer rating, String comment) {
        Order order = ownOrder(orderId);
        int stars = stars(rating);
        if (!deliveredItemIds(order).contains(itemId)) {
            throw new IllegalStateException("You can rate a product once it has been delivered to you.");
        }
        String email = order.getCustomerEmail();
        ProductReview r = reviews.findByUserEmailIgnoreCaseAndItemId(email, itemId).orElse(null);
        Instant now = Instant.now();
        if (r == null) {
            r = new ProductReview();
            r.setItemId(itemId);
            r.setUserEmail(email);
            r.setCreatedAt(now);
        } else {
            r.setUpdatedAt(now);
        }
        r.setOrderId(orderId);
        r.setDisplayName(displayName(email));
        r.setRating(stars);
        r.setComment(text(comment, MAX_COMMENT, "comment"));
        reviews.save(r);

        ItemMaster item = items.findById(itemId).orElse(null);
        String name = item == null ? "Item #" + itemId : item.getItemName();
        if (stars <= 2) {
            notify.withPermission("reviews.manage", new NotificationService.Note("LOW_RATING",
                    stars + "-star review: " + name,
                    r.getDisplayName() + " (order #" + orderId + ")" + (r.getComment() == null ? "" : ": \"" + cut(r.getComment(), 120) + "\""),
                    "/admin/reviews"), false);
        }
        return new RateItem(itemId, name, item == null ? null : item.getImagePath(), true, stars, r.getComment());
    }

    /** The shop's service and the delivery of a delivered order. Rating again changes it. */
    @Transactional
    public MyFeedback rateService(Long orderId, Integer serviceRating, Integer deliveryRating, String comment) {
        Order order = ownOrder(orderId);
        int service = stars(serviceRating);
        int delivery = stars(deliveryRating);
        if (deliveredItemIds(order).isEmpty()) {
            throw new IllegalStateException("You can rate this order once it has been delivered.");
        }
        Instant now = Instant.now();
        OrderFeedback f = feedback.findByOrderId(orderId).orElse(null);
        if (f == null) {
            f = new OrderFeedback();
            f.setOrderId(orderId);
            f.setUserEmail(order.getCustomerEmail());
            f.setCreatedAt(now);
        } else {
            f.setUpdatedAt(now);
        }
        f.setDisplayName(displayName(order.getCustomerEmail()));
        f.setServiceRating(service);
        f.setDeliveryRating(delivery);
        f.setComment(text(comment, MAX_COMMENT, "comment"));
        f.setRiderId(packages.findByOrderIdOrderByIdAsc(orderId).stream()
                .filter(p -> OrderPackage.DELIVERED.equals(p.getStatus()) && p.getRiderId() != null)
                .map(OrderPackage::getRiderId).findFirst().orElse(null));
        feedback.save(f);
        if (Math.min(service, delivery) <= 2) {
            notify.withPermission("reviews.manage", new NotificationService.Note("LOW_RATING",
                    "Low rating for order #" + orderId + ": service " + service + ", delivery " + delivery,
                    f.getDisplayName() + (f.getComment() == null ? "" : ": \"" + cut(f.getComment(), 120) + "\""), "/admin/reviews"), false);
        }
        return new MyFeedback(service, delivery, f.getComment());
    }

    // ================= Staff =================

    @Transactional(readOnly = true)
    public AdminOverview overview() {
        requireStaff();
        Map<Long, String> itemNames = new HashMap<>();
        List<ProductReview> all = reviews.findAllByOrderByCreatedAtDesc();
        items.findAllById(all.stream().map(ProductReview::getItemId).collect(Collectors.toSet()))
                .forEach(i -> itemNames.put(i.getItemId(), i.getItemName()));
        Map<Long, String> riderNames = new HashMap<>();
        riders.findAll().forEach(r -> riderNames.put(r.getId(), r.getUser() == null ? "Driver #" + r.getId() : r.getUser().getName()));

        List<AdminReview> productList = all.stream().map(r -> new AdminReview(r.getId(), r.getItemId(),
                itemNames.getOrDefault(r.getItemId(), "Item #" + r.getItemId()), r.getOrderId(), r.getUserEmail(), r.getDisplayName(),
                r.getRating(), r.getComment(), r.isHidden(), r.getReply(), r.getRepliedBy(), r.getCreatedAt(), r.getUpdatedAt())).toList();
        List<AdminFeedback> feedbackList = feedback.findAllByOrderByCreatedAtDesc().stream().map(f -> new AdminFeedback(f.getId(),
                f.getOrderId(), f.getUserEmail(), f.getDisplayName(), f.getServiceRating(), f.getDeliveryRating(), f.getComment(),
                f.getRiderId(), f.getRiderId() == null ? null : riderNames.get(f.getRiderId()), f.isHidden(), f.getReply(),
                f.getRepliedBy(), f.getCreatedAt(), f.getUpdatedAt())).toList();
        List<RiderRating> riderList = feedback.riderAverages().stream()
                .map(row -> new RiderRating((Long) row[0], riderNames.getOrDefault((Long) row[0], "Driver #" + row[0]),
                        BigDecimal.valueOf(((Number) row[1]).doubleValue()).setScale(1, RoundingMode.HALF_UP), ((Number) row[2]).longValue()))
                .sorted(Comparator.comparing(RiderRating::average))
                .toList();
        return new AdminOverview(productList, feedbackList, riderList);
    }

    @Transactional
    public void setProductReviewHidden(Long id, boolean hidden) {
        requireStaff();
        ProductReview r = reviews.findById(id).orElseThrow(() -> new IllegalStateException("Review not found."));
        r.setHidden(hidden);
        reviews.save(r);
    }

    @Transactional
    public void replyToProductReview(Long id, String reply) {
        requireStaff();
        ProductReview r = reviews.findById(id).orElseThrow(() -> new IllegalStateException("Review not found."));
        r.setReply(text(reply, MAX_REPLY, "reply"));
        r.setRepliedBy(r.getReply() == null ? null : CurrentUser.email());
        r.setRepliedAt(r.getReply() == null ? null : Instant.now());
        reviews.save(r);
        tellCustomer(r.getUserEmail(), r.getReply(), "your review", "/products/" + r.getItemId());
    }

    @Transactional
    public void setFeedbackHidden(Long id, boolean hidden) {
        requireStaff();
        OrderFeedback f = feedback.findById(id).orElseThrow(() -> new IllegalStateException("Review not found."));
        f.setHidden(hidden);
        feedback.save(f);
    }

    @Transactional
    public void replyToFeedback(Long id, String reply) {
        requireStaff();
        OrderFeedback f = feedback.findById(id).orElseThrow(() -> new IllegalStateException("Review not found."));
        f.setReply(text(reply, MAX_REPLY, "reply"));
        f.setRepliedBy(f.getReply() == null ? null : CurrentUser.email());
        f.setRepliedAt(f.getReply() == null ? null : Instant.now());
        feedback.save(f);
        tellCustomer(f.getUserEmail(), f.getReply(), "your feedback on order #" + f.getOrderId(), "/reviews");
    }

    // ================= Helpers =================

    /** The products of this order that reached the customer: everything once the order is complete, otherwise per package. */
    private Set<Long> deliveredItemIds(Order order) {
        if (!"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            return Set.of();
        }
        List<OrderItem> lines = orderItems.findByOrderId(order.getOrderId());
        if ("COMPLETED".equalsIgnoreCase(order.getOrderStatus())) {
            return lines.stream().map(OrderItem::getItemId).collect(Collectors.toSet());
        }
        Set<Long> deliveredPackages = packages.findByOrderIdOrderByIdAsc(order.getOrderId()).stream()
                .filter(p -> OrderPackage.DELIVERED.equals(p.getStatus())).map(OrderPackage::getId).collect(Collectors.toSet());
        return lines.stream().filter(l -> l.getPackageId() != null && deliveredPackages.contains(l.getPackageId()))
                .map(OrderItem::getItemId).collect(Collectors.toSet());
    }

    private Order ownOrder(Long orderId) {
        Order order = orders.findById(orderId == null ? -1L : orderId).orElseThrow(() -> new IllegalStateException("Order not found."));
        String me = CurrentUser.email();
        if (me == null || order.getCustomerEmail() == null || !order.getCustomerEmail().equalsIgnoreCase(me)) {
            throw new AccessDeniedException("You can only rate your own orders.");
        }
        return order;
    }

    private void tellCustomer(String email, String reply, String what, String link) {
        if (reply != null) {
            notify.user(email, new NotificationService.Note("REVIEW_REPLY", "The shop answered " + what, cut(reply, 200), link), false);
        }
    }

    /** "Karma Dorji" -> "Karma D."; one name stays as it is. Only this is shown publicly. */
    private String displayName(String email) {
        String name = users.findByEmail(email).map(User::getName).orElse(null);
        if (name == null || name.isBlank()) {
            name = email.substring(0, Math.max(1, email.indexOf('@')));
        }
        String[] parts = name.trim().split("\\s+");
        String shown = parts.length == 1 ? parts[0] : parts[0] + " " + Character.toUpperCase(parts[parts.length - 1].charAt(0)) + ".";
        return cut(shown, 60);
    }

    private static void requireStaff() {
        if (!CurrentUser.has("reviews.manage")) {
            throw new AccessDeniedException("Only staff who manage reviews can do this.");
        }
    }

    private static int stars(Integer value) {
        if (value == null || value < 1 || value > 5) {
            throw new IllegalArgumentException("Choose from 1 to 5 stars.");
        }
        return value;
    }

    private static String text(String value, int max, String what) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String t = value.trim();
        if (t.length() > max) {
            throw new IllegalArgumentException("The " + what + " is too long (at most " + max + " characters).");
        }
        return t;
    }

    private static BigDecimal average(int sum, int count) {
        return count == 0 ? null : BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static ReviewView view(ProductReview r) {
        return new ReviewView(r.getId(), r.getDisplayName(), r.getRating(), r.getComment(), r.getCreatedAt(), r.getUpdatedAt(),
                r.getReply(), r.getRepliedAt());
    }

    private static FeedbackView view(OrderFeedback f) {
        return new FeedbackView(f.getId(), f.getDisplayName(), f.getServiceRating(), f.getDeliveryRating(), f.getComment(),
                f.getCreatedAt(), f.getReply(), f.getRepliedAt());
    }
}
