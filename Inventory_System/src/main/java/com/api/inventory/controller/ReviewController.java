package com.api.inventory.controller;

import com.api.inventory.service.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Ratings and reviews.
 *   Everyone:  GET /api/reviews/products/{itemId}   a product's average, stars and reviews
 *              GET /api/reviews/summary             average and count of every rated product (stars on cards)
 *              GET /api/reviews/service             service and delivery ratings (Customer reviews page)
 *   Customer:  GET  /api/reviews/orders/{orderId}                       what they can rate in their order
 *              POST /api/reviews/orders/{orderId}/products/{itemId}     {rating, comment}
 *              POST /api/reviews/orders/{orderId}/service               {serviceRating, deliveryRating, comment}
 *   Staff:     GET  /api/reviews/admin                                  everything, with drivers' ratings (reviews.manage)
 *              POST /api/reviews/admin/{products|service}/{id}/hidden   {hidden}
 *              POST /api/reviews/admin/{products|service}/{id}/reply    {reply} (empty removes it)
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    public record ProductRating(Integer rating, String comment) {
    }

    public record ServiceRating(Integer serviceRating, Integer deliveryRating, String comment) {
    }

    public record HiddenRequest(boolean hidden) {
    }

    public record ReplyRequest(String reply) {
    }

    @GetMapping("/products/{itemId}")
    public ReviewService.ProductSummary product(@PathVariable Long itemId) {
        return reviews.product(itemId);
    }

    @GetMapping("/summary")
    public List<ReviewService.ItemSummary> summary() {
        return reviews.summaries();
    }

    @GetMapping("/service")
    public ReviewService.ServiceSummary service() {
        return reviews.service();
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/orders/{orderId}")
    public ReviewService.OrderRating forOrder(@PathVariable Long orderId) {
        return reviews.forOrder(orderId);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/orders/{orderId}/products/{itemId}")
    public ReviewService.RateItem rateProduct(@PathVariable Long orderId, @PathVariable Long itemId, @RequestBody ProductRating request) {
        return reviews.rateProduct(orderId, itemId, request.rating(), request.comment());
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/orders/{orderId}/service")
    public ReviewService.MyFeedback rateService(@PathVariable Long orderId, @RequestBody ServiceRating request) {
        return reviews.rateService(orderId, request.serviceRating(), request.deliveryRating(), request.comment());
    }

    @PreAuthorize("hasAuthority('reviews.manage')")
    @GetMapping("/admin")
    public ReviewService.AdminOverview admin() {
        return reviews.overview();
    }

    @PreAuthorize("hasAuthority('reviews.manage')")
    @PostMapping("/admin/products/{id}/hidden")
    public ResponseEntity<Void> hideProductReview(@PathVariable Long id, @RequestBody HiddenRequest request) {
        reviews.setProductReviewHidden(id, request.hidden());
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('reviews.manage')")
    @PostMapping("/admin/products/{id}/reply")
    public ResponseEntity<Void> replyProductReview(@PathVariable Long id, @RequestBody ReplyRequest request) {
        reviews.replyToProductReview(id, request.reply());
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('reviews.manage')")
    @PostMapping("/admin/service/{id}/hidden")
    public ResponseEntity<Void> hideFeedback(@PathVariable Long id, @RequestBody HiddenRequest request) {
        reviews.setFeedbackHidden(id, request.hidden());
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('reviews.manage')")
    @PostMapping("/admin/service/{id}/reply")
    public ResponseEntity<Void> replyFeedback(@PathVariable Long id, @RequestBody ReplyRequest request) {
        reviews.replyToFeedback(id, request.reply());
        return ResponseEntity.ok().build();
    }
}
