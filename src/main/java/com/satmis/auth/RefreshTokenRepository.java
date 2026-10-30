package com.satmis.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.user.id = :userId and r.revoked = false")
    void revokeAllByUserId(@Param("userId") Long userId);

    // Conditional so that of several requests racing with the same token exactly one rotates it
    @Modifying
    @Query("update RefreshToken r set r.revoked = true, r.rotatedAt = :now where r.id = :id and r.revoked = false")
    int rotate(@Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query("delete from RefreshToken r where r.expiryDate < :cutoff")
    int deleteAllExpiredBefore(@Param("cutoff") Instant cutoff);
}
