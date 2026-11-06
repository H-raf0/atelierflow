package dev.atelierflow.auth;

import dev.atelierflow.user.Role;

/**
 * The caller, as read from a validated access token. Controllers receive it with
 * {@code @AuthenticationPrincipal} — no database access is needed to know who is calling.
 */
public record AuthenticatedUser(Long id, String username, String fullName, Role role) {

    public boolean is(Role expected) {
        return role == expected;
    }
}
