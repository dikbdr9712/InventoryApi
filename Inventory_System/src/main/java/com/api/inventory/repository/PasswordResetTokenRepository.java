package com.api.inventory.repository;

import com.api.inventory.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Locked, so a link pressed twice at the same moment is only used once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t where t.tokenHash = :hash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("hash") String hash);

    List<PasswordResetToken> findByUserIdAndUsedAtIsNull(Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, Instant after);

    long countByRequestIpAndCreatedAtAfter(String requestIp, Instant after);

    @Modifying
    @Query("delete from PasswordResetToken t where t.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
