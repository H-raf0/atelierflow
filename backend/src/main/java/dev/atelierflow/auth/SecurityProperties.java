package dev.atelierflow.auth;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Security settings, bound from {@code atelierflow.security.*}.
 *
 * @param jwtSecret          Base64-encoded HMAC key (at least 256 bits), injected through the
 *                           {@code JWT_SECRET} environment variable — never committed
 * @param accessTokenTtl     lifetime of the JWT access token: short, as it cannot be revoked
 * @param refreshTokenTtl    lifetime of the refresh token (revocable, stored server-side)
 * @param refreshCookieSecure whether the refresh cookie requires HTTPS
 * @param corsAllowedOrigins origins allowed to call the API from a browser when the frontend is
 *                           served from another origin; empty when nginx serves both (same origin)
 */
@ConfigurationProperties("atelierflow.security")
public record SecurityProperties(
        String jwtSecret,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("7d") Duration refreshTokenTtl,
        @DefaultValue("true") boolean refreshCookieSecure,
        @DefaultValue List<String> corsAllowedOrigins) {

    private static final int MIN_KEY_BYTES = 32;

    public SecurityProperties {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "No JWT signing key: set the JWT_SECRET environment variable (Base64, at least 256 bits)");
        }
        if (Base64.getDecoder().decode(jwtSecret).length < MIN_KEY_BYTES) {
            throw new IllegalStateException("JWT_SECRET must encode at least 256 bits (32 bytes)");
        }
    }

    SecretKey signingKey() {
        return new SecretKeySpec(Base64.getDecoder().decode(jwtSecret), "HmacSHA256");
    }
}
