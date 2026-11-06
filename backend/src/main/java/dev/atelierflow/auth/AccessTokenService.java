package dev.atelierflow.auth;

import dev.atelierflow.user.AppUser;
import dev.atelierflow.user.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Issues the short-lived JWT access tokens and turns a validated JWT back into an authentication.
 *
 * <p>The token is self-contained (user id, name and role in the claims): each request is authorized
 * by checking the HMAC signature and expiry, without a session or a database lookup.
 */
@Component
public class AccessTokenService implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String ISSUER = "atelierflow";
    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_NAME = "name";

    private final JwtEncoder encoder;
    private final SecurityProperties properties;
    private final Clock clock;

    public AccessTokenService(JwtEncoder encoder, SecurityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(AppUser user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .subject(user.getUsername())
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_NAME, user.getFullName())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long expiresInSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }

    /** Validated JWT → authentication carrying an {@link AuthenticatedUser} and a {@code ROLE_*} authority. */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Role role = Role.valueOf(jwt.getClaimAsString(CLAIM_ROLE));
        Number userId = jwt.getClaim(CLAIM_USER_ID);
        var principal = new AuthenticatedUser(userId.longValue(), jwt.getSubject(), jwt.getClaimAsString(CLAIM_NAME), role);
        return new AuthenticatedUserToken(principal, jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
}
