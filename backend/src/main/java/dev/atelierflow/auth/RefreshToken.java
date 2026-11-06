package dev.atelierflow.auth;

import dev.atelierflow.common.persistence.BaseEntity;
import dev.atelierflow.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/** Server-side record of a refresh token; only the SHA-256 of the token value is stored. */
@Entity
@Table(name = "refresh_token")
class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    @Column(nullable = false, unique = true, updatable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    /** Required by JPA. */
    protected RefreshToken() {
    }

    RefreshToken(AppUser user, String tokenHash, Instant expiresAt) {
        this.user = Objects.requireNonNull(user);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    boolean isRevoked() {
        return revokedAt != null;
    }

    boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    AppUser getUser() {
        return user;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }
}
