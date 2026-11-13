package com.btl.transport.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, Integer> {
    Optional<PasswordReset> findByTokenHash(String tokenHash);

    Optional<PasswordReset> findFirstByAdminUserIdOrderByCreatedAtDesc(Integer adminUserId);

    /** Retires every open link for the account (a newer link was sent, or the password was just changed). */
    @Modifying
    @Query("UPDATE PasswordReset r SET r.usedAt = :now WHERE r.adminUserId = :userId AND r.usedAt IS NULL")
    int retireOpenLinks(@Param("userId") Integer userId, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("DELETE FROM PasswordReset r WHERE r.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") OffsetDateTime before);
}
