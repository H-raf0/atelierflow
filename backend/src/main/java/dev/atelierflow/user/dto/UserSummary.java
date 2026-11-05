package dev.atelierflow.user.dto;

import dev.atelierflow.user.Role;

public record UserSummary(Long id, String username, String fullName, Role role) {
}
