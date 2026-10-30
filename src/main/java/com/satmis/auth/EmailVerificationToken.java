package com.satmis.auth;

import com.satmis.common.BaseEntity;
import com.satmis.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "email_verification_tokens",
        indexes = @Index(name = "idx_email_verification_tokens_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class EmailVerificationToken extends BaseEntity {

    // SHA-256 hex of the raw token; the raw value only ever exists in the e-mail
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiryDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;
}
