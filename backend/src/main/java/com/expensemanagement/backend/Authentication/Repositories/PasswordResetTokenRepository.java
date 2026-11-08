package com.expensemanagement.backend.Authentication.Repositories;

import com.expensemanagement.backend.Authentication.Entities.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID>
{
    /** The raw token is hashed by the service, so the lookup is by hash and an index hit. */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Marks every still-usable token of one user as spent.
     *
     * <p>Called both when a new token is issued and when one is redeemed, so at most one link
     * per account is ever live.
     */
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.user.id = :userId and t.usedAt is null")
    int invalidateAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
