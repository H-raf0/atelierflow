package dev.atelierflow.auth;

import dev.atelierflow.common.error.UnauthorizedException;
import dev.atelierflow.user.AppUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh tokens with rotation and reuse detection.
 *
 * <ul>
 *   <li>A token is a 256-bit random value; only its SHA-256 hash is stored.</li>
 *   <li>Each refresh <em>rotates</em> the token: the presented one is revoked and a new one issued.</li>
 *   <li>Presenting an already revoked token means it was stolen and replayed (or the legitimate user
 *       replays an old copy after the thief used it): every session of that user is then revoked.</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final SecurityProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, SecurityProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /** A rotated token: the user it belongs to and the new raw value to send to the client. */
    public record Rotation(AppUser user, String newToken) {
    }

    @Transactional
    public String issue(AppUser user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new RefreshToken(user, sha256(token), clock.instant().plus(properties.refreshTokenTtl())));
        return token;
    }

    /**
     * Runs in its own transaction which is committed even when the token is rejected: the
     * revocation of all sessions after a reuse must survive the 401 that follows.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = UnauthorizedException.class)
    public Rotation rotate(String token) {
        Instant now = clock.instant();
        RefreshToken current = repository.findByTokenHash(sha256(token))
                .orElseThrow(RefreshTokenService::invalid);
        if (current.isRevoked()) {
            int revoked = repository.revokeAllOfUser(current.getUser().getId(), now);
            LOG.warn("Reuse of a revoked refresh token for user {}: {} session(s) revoked",
                    current.getUser().getUsername(), revoked);
            throw invalid();
        }
        if (current.isExpired(now)) {
            throw invalid();
        }
        current.revoke(now);
        return new Rotation(current.getUser(), issue(current.getUser()));
    }

    @Transactional
    public void revoke(String token) {
        repository.findByTokenHash(sha256(token)).ifPresent(t -> t.revoke(clock.instant()));
    }

    /** Housekeeping: expired tokens are useless, even for reuse detection, after their expiry. */
    @Scheduled(cron = "${atelierflow.security.refresh-token-purge-cron:0 30 3 * * *}")
    @Transactional
    public void purgeExpired() {
        int deleted = repository.deleteExpiredBefore(clock.instant());
        LOG.info("Purged {} expired refresh token(s)", deleted);
    }

    private static UnauthorizedException invalid() {
        return new UnauthorizedException("INVALID_REFRESH_TOKEN", "The session has expired, please sign in again.");
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is guaranteed by the JVM specification", e);
        }
    }
}
