package com.api.inventory.service;

import com.api.inventory.dto.ReturnDTO.ReturnableLine;
import com.api.inventory.dto.ReturnDTO.ReturnableOrder;
import com.api.inventory.entity.ItemMaster;
import com.api.inventory.entity.Order;
import com.api.inventory.entity.ReturnRequest;
import com.api.inventory.entity.User;
import com.api.inventory.exception.ResourceNotFoundException;
import com.api.inventory.repository.ItemMasterRepository;
import com.api.inventory.repository.OrderRepository;
import com.api.inventory.repository.ReturnRequestRepository;
import com.api.inventory.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Customers ask to return items of a completed online order, within the return window (the same rules as a return
 * at the counter: SalesReturnService). Staff approve or decline; when staff record the return and refund for the
 * order, the request is done and the customer is told the amount.
 */
@Service
public class ReturnRequestService {

    private static final Set<String> REASONS = Set.of("DAMAGED", "WRONG_ITEM", "CHANGED_MIND", "OTHER");
    private static final Map<String, String> REASON_WORDS = Map.of("DAMAGED", "damaged", "WRONG_ITEM", "wrong item",
            "CHANGED_MIND", "changed their mind", "OTHER", "another reason");
    private static final List<String> OPEN = List.of(ReturnRequest.REQUESTED, ReturnRequest.APPROVED);

    private final ReturnRequestRepository requests;
    private final OrderRepository orders;
    private final ItemMasterRepository items;
    private final UserRepository users;
    private final SalesReturnService salesReturns;
    private final NotificationService notify;

    public ReturnRequestService(ReturnRequestRepository requests, OrderRepository orders, ItemMasterRepository items,
                                UserRepository users, SalesReturnService salesReturns, NotificationService notify) {
        this.requests = requests;
        this.orders = orders;
        this.items = items;
        this.users = users;
        this.salesReturns = salesReturns;
        this.notify = notify;
    }

    public record LineView(Long orderItemId, Long itemId, String itemName, int quantity) {
    }

    /** customerEmail and decidedBy: staff only (empty for the customer). */
    public record RequestView(Long id, Long orderId, String customerName, String customerEmail, String reason, String details,
                              String status, String staffNote, String decidedBy, Instant decidedAt, Instant createdAt,
                              List<LineView> lines) {
    }

    public record Returnable(Long orderItemId, Long itemId, String itemName, long quantityLeft) {
    }

    /** What the customer's order page needs: may they ask, why not, what, and their requests so far. */
    public record CustomerView(boolean canRequest, String message, long daysLeft, List<Returnable> lines, List<RequestView> requests) {
    }

    public record LineAsk(Long orderItemId, Integer quantity) {
    }

    public record Ask(String reason, String details, List<LineAsk> items) {
    }

    // ------------------------------------------------------------------ the customer

    @Transactional(readOnly = true)
    public CustomerView forCustomer(Long orderId, String email) {
        Order order = own(orderId, email);
        ReturnableOrder r = salesReturns.returnable(orderId);
        Map<Long, String> names = names(r.lines().stream().map(ReturnableLine::itemId).toList());
        List<Returnable> lines = r.lines().stream()
                .map(l -> new Returnable(l.orderItemId(), l.itemId(), names.getOrDefault(l.itemId(), "Product"), l.quantityLeft()))
                .toList();
        List<RequestView> mine = requests.findByOrderIdOrderByCreatedAtDesc(orderId).stream().map(q -> view(q, order, false)).toList();
        String why = whyNot(r, mine, lines);
        return new CustomerView(why == null, why, r.eligible() ? r.daysLeft() : 0, lines, mine);
    }

    @Transactional
    public RequestView ask(Long orderId, String email, Ask ask) {
        Order order = own(orderId, email);
        String reason = ask == null || ask.reason() == null ? "" : ask.reason().trim().toUpperCase();
        if (!REASONS.contains(reason)) {
            throw new IllegalArgumentException("Choose why you want to return it.");
        }
        String details = ask.details() == null ? "" : ask.details().trim();
        if (details.length() > 500) {
            throw new IllegalArgumentException("Please keep the details under 500 characters.");
        }
        if ("OTHER".equals(reason) && details.isEmpty()) {
            throw new IllegalArgumentException("Tell us a little about the reason.");
        }
        ReturnableOrder r = salesReturns.returnable(orderId);
        Map<Long, ReturnableLine> byLine = r.lines().stream().collect(Collectors.toMap(ReturnableLine::orderItemId, l -> l));
        List<RequestView> before = requests.findByOrderIdOrderByCreatedAtDesc(orderId).stream().map(q -> view(q, order, false)).toList();
        String why = whyNot(r, before, r.lines().stream().map(l -> new Returnable(l.orderItemId(), l.itemId(), "", l.quantityLeft())).toList());
        if (why != null) {
            throw new IllegalStateException(why);
        }

        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (LineAsk line : ask.items() == null ? List.<LineAsk>of() : ask.items()) {
            if (line == null || line.orderItemId() == null || line.quantity() == null || line.quantity() <= 0) {
                continue;
            }
            wanted.merge(line.orderItemId(), line.quantity(), Integer::sum);
        }
        if (wanted.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one product to return.");
        }
        Map<Long, String> names = names(r.lines().stream().map(ReturnableLine::itemId).toList());
        ReturnRequest q = new ReturnRequest();
        q.setOrderId(orderId);
        q.setUserEmail(order.getCustomerEmail());
        q.setReason(reason);
        q.setDetails(details.isEmpty() ? null : details);
        q.setCreatedAt(Instant.now());
        int count = 0;
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            ReturnableLine line = byLine.get(e.getKey());
            if (line == null) {
                throw new IllegalArgumentException("That product is not part of this order.");
            }
            if (e.getValue() > line.quantityLeft()) {
                throw new IllegalArgumentException("You can return at most " + line.quantityLeft() + " of "
                        + names.getOrDefault(line.itemId(), "this product") + ".");
            }
            q.getLines().add(new ReturnRequest.Line(line.orderItemId(), line.itemId(), names.getOrDefault(line.itemId(), "Product"), e.getValue()));
            count += e.getValue();
        }
        ReturnRequest saved = requests.save(q);
        notify.withPermission("sales.return", new NotificationService.Note("RETURN_REQUEST",
                "Return request for order #" + orderId,
                (order.getCustomerName() == null ? "A customer" : order.getCustomerName()) + " asks to return " + count
                        + (count == 1 ? " item" : " items") + " (" + REASON_WORDS.get(reason) + ").",
                "/admin/return-requests"), false);
        return view(saved, order, false);
    }

    // ------------------------------------------------------------------ staff

    /** open: waiting or approved; closed: done or declined; all: everything. Newest first. */
    @Transactional(readOnly = true)
    public List<RequestView> list(String which) {
        List<String> statuses = switch (which == null ? "open" : which) {
            case "closed" -> List.of(ReturnRequest.DONE, ReturnRequest.DECLINED);
            case "all" -> List.of(ReturnRequest.REQUESTED, ReturnRequest.APPROVED, ReturnRequest.DONE, ReturnRequest.DECLINED);
            default -> OPEN;
        };
        List<ReturnRequest> found = requests.findByStatusInOrderByCreatedAtDesc(statuses);
        Map<Long, Order> byOrder = orders.findAllById(found.stream().map(ReturnRequest::getOrderId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Order::getOrderId, o -> o));
        return found.stream().map(q -> view(q, byOrder.get(q.getOrderId()), true)).toList();
    }

    @Transactional
    public RequestView approve(Long id, String note, String staffEmail) {
        ReturnRequest q = requests.findById(id).orElseThrow(() -> new ResourceNotFoundException("That request was not found."));
        if (!ReturnRequest.REQUESTED.equals(q.getStatus())) {
            throw new IllegalStateException("This request was already answered.");
        }
        String answer = clean(note);
        q.setStatus(ReturnRequest.APPROVED);
        q.setStaffNote(answer);
        q.setDecidedBy(staffEmail);
        q.setDecidedAt(Instant.now());
        requests.save(q);
        Order order = orders.findById(q.getOrderId()).orElse(null);
        notify.customer(order, new NotificationService.Note("RETURN_APPROVED", "Return approved: order #" + q.getOrderId(),
                answer != null ? answer : "We will contact you to collect the items. Please keep them with their packaging.",
                "/orders/" + q.getOrderId()), true, null);
        return view(q, order, true);
    }

    @Transactional
    public RequestView decline(Long id, String note, String staffEmail) {
        ReturnRequest q = requests.findById(id).orElseThrow(() -> new ResourceNotFoundException("That request was not found."));
        if (!OPEN.contains(q.getStatus())) {
            throw new IllegalStateException("This request is already closed.");
        }
        String answer = clean(note);
        if (answer == null) {
            throw new IllegalArgumentException("Tell the customer why (they will see it).");
        }
        q.setStatus(ReturnRequest.DECLINED);
        q.setStaffNote(answer);
        q.setDecidedBy(staffEmail);
        q.setDecidedAt(Instant.now());
        requests.save(q);
        Order order = orders.findById(q.getOrderId()).orElse(null);
        notify.customer(order, new NotificationService.Note("RETURN_DECLINED", "Return request declined: order #" + q.getOrderId(),
                answer, "/orders/" + q.getOrderId()), true, null);
        return view(q, order, true);
    }

    /** Staff recorded a return for the order: its open requests are done, and the customer hears the amount. */
    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReturnRecorded(SalesReturnService.ReturnRecorded event) {
        List<ReturnRequest> open = requests.findByOrderIdAndStatusIn(event.orderId(), OPEN);
        if (open.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (ReturnRequest q : open) {
            q.setStatus(ReturnRequest.DONE);
            q.setDecidedAt(now);
            requests.save(q);
        }
        BigDecimal amount = event.refundAmount() == null ? BigDecimal.ZERO : event.refundAmount();
        String how = "CASH".equals(event.refundMethod()) ? " in cash." : " to the account you paid from.";
        notify.customer(orders.findById(event.orderId()).orElse(null), new NotificationService.Note("RETURN_DONE",
                "Return done: order #" + event.orderId(), "We refunded Nu. " + amount.toPlainString() + how,
                "/orders/" + event.orderId()), true, null);
    }

    // ------------------------------------------------------------------ helpers

    private Order own(Long orderId, String email) {
        Order order = orders.findById(orderId).orElse(null);
        if (order == null || order.getCustomerEmail() == null || !order.getCustomerEmail().equalsIgnoreCase(email)) {
            throw new ResourceNotFoundException("That order was not found.");
        }
        return order;
    }

    /** Why this customer cannot ask now, in their words; null when they can. */
    private static String whyNot(ReturnableOrder r, List<RequestView> requests, List<Returnable> lines) {
        if (requests.stream().anyMatch(q -> OPEN.contains(q.status()))) {
            return "You already asked for a return of this order. We will answer you soon.";
        }
        if (!"COMPLETED".equalsIgnoreCase(r.orderStatus())) {
            return "You can ask for a return once your whole order has reached you.";
        }
        if (!r.eligible()) {
            return "The time for returns has passed: returns are accepted within 7 days of ordering.";
        }
        if (lines.stream().noneMatch(l -> l.quantityLeft() > 0)) {
            return "Everything in this order has already been returned.";
        }
        return null;
    }

    private RequestView view(ReturnRequest q, Order order, boolean staff) {
        String decidedBy = null;
        if (staff && q.getDecidedBy() != null) {
            decidedBy = users.findByEmail(q.getDecidedBy()).map(User::getName).orElse(q.getDecidedBy());
        }
        return new RequestView(q.getId(), q.getOrderId(), order == null ? null : order.getCustomerName(),
                staff ? q.getUserEmail() : null, q.getReason(), q.getDetails(), q.getStatus(), q.getStaffNote(), decidedBy,
                q.getDecidedAt(), q.getCreatedAt(),
                q.getLines().stream().map(l -> new LineView(l.getOrderItemId(), l.getItemId(), l.getItemName(), l.getQuantity())).toList());
    }

    private Map<Long, String> names(Collection<Long> itemIds) {
        return items.findAllById(new HashSet<>(itemIds)).stream().collect(Collectors.toMap(ItemMaster::getItemId, ItemMaster::getItemName, (a, b) -> a));
    }

    private static String clean(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return t.length() > 300 ? t.substring(0, 300) : t;
    }
}
