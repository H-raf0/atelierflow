package dev.atelierflow.auth;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Authentication built from a valid access token, whose principal is the {@link AuthenticatedUser}. */
class AuthenticatedUserToken extends AbstractAuthenticationToken {

    private final AuthenticatedUser principal;
    private final transient Jwt jwt;

    AuthenticatedUserToken(AuthenticatedUser principal, Jwt jwt, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return principal;
    }

    @Override
    public Jwt getCredentials() {
        return jwt;
    }

    @Override
    public String getName() {
        return principal.username();
    }
}
