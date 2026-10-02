package com.api.inventory.security;

import com.api.inventory.entity.Order;
import com.api.inventory.service.OrderService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Whose order is this? Stops one customer from reading or cancelling another customer's order.
 * A person may touch an order if it is THEIR order (same email), or if they have a staff permission that allows it.
 */
@Component
public class OrderAccess {

    /** Until the shop starts working on an order, its customer may still cancel it. */
    private static final Set<String> CANCELLABLE_BY_CUSTOMER = Set.of("CREATED", "PENDING");

    private final OrderService orderService;

    public OrderAccess(OrderService orderService) {
        this.orderService = orderService;
    }

    /** The order, if the caller owns it or has one of these permissions. Otherwise the request is refused. */
    public Order requireOwnerOrPermission(Long orderId, String... permissions) {
        requireSignedIn();                                  // before looking anything up
        Order order = orderService.getOrderById(orderId);
        if (hasAny(permissions) || isOwner(order)) {
            return order;
        }
        throw new AccessDeniedException("This is not your order.");
    }

    /** For "my orders": the email in the address must be the caller's own, unless they have one of these permissions. */
    public void requireSelfOrPermission(String email, String... permissions) {
        requireSignedIn();
        if (hasAny(permissions) || sameEmail(email, CurrentUser.email())) {
            return;
        }
        throw new AccessDeniedException("These are not your orders.");
    }

    /** For placing an order: the email on it must be the signed-in person's own. Nobody orders in someone else's name. */
    public void requireOwnEmail(String email) {
        requireSignedIn();
        if (!sameEmail(email, CurrentUser.email())) {
            throw new AccessDeniedException("An order can only be placed for your own account.");
        }
    }

    /**
     * For cancelling: staff who fulfil orders may cancel any order. A customer may cancel only their own,
     * and only while it is still new or waiting for payment.
     */
    public Order requireCancellable(Long orderId) {
        Order order = requireOwnerOrPermission(orderId, "orders.fulfil");
        if (!CurrentUser.has("orders.fulfil")) {
            String status = order.getOrderStatus() == null ? "" : order.getOrderStatus().trim().toUpperCase();
            if (!CANCELLABLE_BY_CUSTOMER.contains(status)) {
                throw new IllegalStateException("This order can no longer be cancelled. Please contact us.");
            }
        }
        return order;
    }

    public boolean isOwner(Order order) {
        return order != null && sameEmail(order.getCustomerEmail(), CurrentUser.email());
    }

    private static boolean hasAny(String... permissions) {
        for (String permission : permissions) {
            if (CurrentUser.has(permission)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameEmail(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    private static void requireSignedIn() {
        if (CurrentUser.email() == null) {
            throw new AccessDeniedException("Please sign in.");
        }
    }
}