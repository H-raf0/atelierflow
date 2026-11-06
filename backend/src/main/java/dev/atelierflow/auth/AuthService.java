package dev.atelierflow.auth;

import dev.atelierflow.common.error.ResourceNotFoundException;
import dev.atelierflow.user.AppUser;
import dev.atelierflow.user.UserMapper;
import dev.atelierflow.user.UserRepository;
import dev.atelierflow.user.dto.UserSummary;
import java.time.Duration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sign-in, session renewal and sign-out. */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final UserMapper userMapper;
    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;
    private final SecurityProperties properties;

    public AuthService(AuthenticationManager authenticationManager, UserRepository users, UserMapper userMapper,
                       AccessTokenService accessTokens, RefreshTokenService refreshTokens,
                       SecurityProperties properties) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.userMapper = userMapper;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.properties = properties;
    }

    /** Tokens of an open session; the refresh token goes into an HttpOnly cookie, never in the body. */
    public record Session(String accessToken, long expiresInSeconds, String refreshToken, Duration refreshTokenTtl,
                          UserSummary user) {
    }

    /**
     * Checks the credentials through Spring Security ({@code DaoAuthenticationProvider} + BCrypt);
     * a wrong password throws {@code BadCredentialsException}, answered with 401.
     */
    @Transactional
    public Session login(String username, String password) {
        authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        AppUser user = users.findByUsername(username).orElseThrow();
        return open(user, refreshTokens.issue(user));
    }

    /** Not transactional itself: the rotation commits in its own transaction (see {@link RefreshTokenService#rotate}). */
    public Session refresh(String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(refreshToken);
        return open(rotation.user(), rotation.newToken());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    @Transactional(readOnly = true)
    public UserSummary me(AuthenticatedUser caller) {
        return users.findById(caller.id())
                .map(userMapper::toSummary)
                .orElseThrow(() -> new ResourceNotFoundException("User", caller.id()));
    }

    private Session open(AppUser user, String refreshToken) {
        return new Session(accessTokens.issue(user), accessTokens.expiresInSeconds(), refreshToken,
                properties.refreshTokenTtl(), userMapper.toSummary(user));
    }
}
