package com.api.inventory.repository;

import com.api.inventory.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserEmailOrderByCreatedAtDesc(String userEmail, Pageable page);

    long countByUserEmailAndReadAtIsNull(String userEmail);

    Optional<Notification> findByIdAndUserEmail(Long id, String userEmail);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.userEmail = :email and n.readAt is null")
    int markAllRead(@Param("email") String email, @Param("now") Instant now);

    @Modifying
    @Query("delete from Notification n where n.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
