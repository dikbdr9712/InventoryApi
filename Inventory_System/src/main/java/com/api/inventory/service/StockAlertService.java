package com.api.inventory.service;

import com.api.inventory.entity.ItemMaster;
import com.api.inventory.entity.StockAlert;
import com.api.inventory.repository.ItemMasterRepository;
import com.api.inventory.repository.StockAlertRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;

/**
 * "Notify me" on sold-out products. When a product's stock goes from nothing to some (a delivery, a return put
 * back, a count), everyone waiting is told once, after the stock change is saved.
 */
@Service
public class StockAlertService {

    private final StockAlertRepository alerts;
    private final ItemMasterRepository items;
    private final NotificationService notify;

    public StockAlertService(StockAlertRepository alerts, ItemMasterRepository items, NotificationService notify) {
        this.alerts = alerts;
        this.items = items;
        this.notify = notify;
    }

    /** The products this person is waiting for. */
    public List<Long> waiting(String email) {
        return alerts.findByUserEmailIgnoreCaseAndNotifiedAtIsNull(email).stream().map(StockAlert::getItemId).toList();
    }

    /** Asking again after being told starts waiting again. */
    @Transactional
    public void ask(String email, Long itemId) {
        if (!items.existsById(itemId)) {
            throw new IllegalStateException("This product does not exist any more.");
        }
        StockAlert alert = alerts.findByUserEmailIgnoreCaseAndItemId(email, itemId).orElseGet(StockAlert::new);
        alert.setUserEmail(alert.getUserEmail() == null ? email : alert.getUserEmail());
        alert.setItemId(itemId);
        alert.setCreatedAt(Instant.now());
        alert.setNotifiedAt(null);
        try {
            alerts.save(alert);
        } catch (DataIntegrityViolationException twice) {
            // two taps at once: the other one saved it
        }
    }

    @Transactional
    public void cancel(String email, Long itemId) {
        alerts.findByUserEmailIgnoreCaseAndItemId(email, itemId).ifPresent(alerts::delete);
    }

    /** The product is back on the shelf: tell everyone waiting (only for a product that is on sale). */
    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBackInStock(StockService.BackInStock event) {
        ItemMaster item = items.findById(event.itemId()).orElse(null);
        if (item == null || Boolean.FALSE.equals(item.getIsActive())) {
            return;
        }
        List<StockAlert> waiting = alerts.findByItemIdAndNotifiedAtIsNull(item.getItemId());
        Instant now = Instant.now();
        for (StockAlert alert : waiting) {
            notify.user(alert.getUserEmail(), new NotificationService.Note("BACK_IN_STOCK",
                    item.getItemName() + " is back in stock",
                    "You asked us to tell you. Order it before it sells out again.",
                    "/products/" + item.getItemId()), true);
            alert.setNotifiedAt(now);
            alerts.save(alert);
        }
    }
}
