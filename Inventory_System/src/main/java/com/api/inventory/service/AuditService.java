package com.api.inventory.service;

import com.api.inventory.entity.AuditLog;
import com.api.inventory.repository.AuditLogRepository;
import com.api.inventory.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Writes the activity log. Never throws: a problem with the log must not stop the real work. */
@Service
public class AuditService {

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    public void record(String action, String target, String details) {
        try {
            AuditLog row = new AuditLog();
            row.setAt(Instant.now());
            String who = CurrentUser.email();
            row.setActor(who == null ? "system" : who);
            row.setAction(action);
            row.setTarget(cut(target, 200));
            row.setDetails(cut(details, 1000));
            repo.save(row);
        } catch (RuntimeException e) {
            System.err.println("Audit log write failed: " + e.getMessage());
        }
    }

    public List<AuditLog> latest(int count) {
        return repo.findAllByOrderByIdDesc(PageRequest.of(0, Math.max(1, Math.min(count, 500))));
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
