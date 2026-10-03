package com.api.inventory.controller;

import com.api.inventory.security.CurrentUser;
import com.api.inventory.service.NotificationService;
import com.api.inventory.service.NotificationService.NotificationView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** The signed-in person's own notifications (the bell at the top of the site). */
@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public Map<String, Object> mine(@RequestParam(defaultValue = "30") int limit) {
        String me = CurrentUser.email();
        List<NotificationView> list = notifications.latest(me, limit);
        return Map.of("items", list, "unread", notifications.unread(me));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("unread", notifications.unread(CurrentUser.email()));
    }

    @PostMapping("/{id}/read")
    public Map<String, Long> read(@PathVariable Long id) {
        notifications.markRead(CurrentUser.email(), id);
        return Map.of("unread", notifications.unread(CurrentUser.email()));
    }

    @PostMapping("/read-all")
    public Map<String, Long> readAll() {
        notifications.markAllRead(CurrentUser.email());
        return Map.of("unread", 0L);
    }
}
