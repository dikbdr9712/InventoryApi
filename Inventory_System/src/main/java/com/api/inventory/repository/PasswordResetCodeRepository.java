package com.api.inventory.repository;

import com.api.inventory.entity.PasswordResetCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    List<PasswordResetCode> findByUserIdAndUsedAtIsNull(Long userId);

    long countByUserIdAndChannelAndCreatedAtAfter(Long userId, String channel, Instant after);

    Optional<PasswordResetCode> findFirstByUserIdAndChannelOrderByCreatedAtDesc(Long userId, String channel);

    /** The codes that still work, newest first. Locked, so two tries at the same moment are both counted. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PasswordResetCode c where c.userId = :userId and c.channel = :channel"
            + " and c.usedAt is null and c.expiresAt > :now order by c.createdAt desc")
    List<PasswordResetCode> findWorkingForUpdate(@Param("userId") Long userId, @Param("channel") String channel,
                                                 @Param("now") Instant now);

    @Modifying
    @Query("delete from PasswordResetCode c where c.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
