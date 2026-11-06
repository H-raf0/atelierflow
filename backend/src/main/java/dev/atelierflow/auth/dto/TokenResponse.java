package dev.atelierflow.auth.dto;

import dev.atelierflow.user.dto.UserSummary;

/**
 * Body of a successful sign-in or refresh. The refresh token is not here: it is set as an HttpOnly
 * cookie, out of reach of JavaScript (and therefore of an XSS payload).
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserSummary user) {
}
