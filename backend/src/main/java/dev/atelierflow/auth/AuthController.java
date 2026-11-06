package dev.atelierflow.auth;

import dev.atelierflow.auth.dto.LoginRequest;
import dev.atelierflow.auth.dto.TokenResponse;
import dev.atelierflow.common.error.UnauthorizedException;
import dev.atelierflow.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AuthController.BASE_PATH)
@Tag(name = "Authentication", description = "JWT access token + rotating refresh token (HttpOnly cookie)")
class AuthController {

    static final String BASE_PATH = "/api/auth";
    static final String REFRESH_COOKIE = "atelierflow_refresh";

    private final AuthService authService;
    private final SecurityProperties properties;

    AuthController(AuthService authService, SecurityProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in: returns an access token and sets the refresh cookie")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return respond(authService.login(request.username(), request.password()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange the refresh cookie for a new access token (the cookie is rotated)")
    ResponseEntity<TokenResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException("INVALID_REFRESH_TOKEN", "No session to refresh, please sign in.");
        }
        return respond(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign out: revokes the refresh token and clears the cookie")
    ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in user")
    UserSummary me(@AuthenticationPrincipal AuthenticatedUser caller) {
        return authService.me(caller);
    }

    private ResponseEntity<TokenResponse> respond(AuthService.Session session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken(), session.refreshTokenTtl()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new TokenResponse(session.accessToken(), "Bearer", session.expiresInSeconds(), session.user()));
    }

    /**
     * HttpOnly: unreadable by JavaScript. SameSite=Strict: never sent by cross-site requests, which
     * protects the cookie-authenticated refresh/logout endpoints against CSRF. Path: only sent to /api/auth.
     */
    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.refreshCookieSecure())
                .sameSite("Strict")
                .path(BASE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
