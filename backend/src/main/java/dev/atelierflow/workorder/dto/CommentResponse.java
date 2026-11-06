package dev.atelierflow.workorder.dto;

import dev.atelierflow.user.dto.UserSummary;
import java.time.Instant;

public record CommentResponse(Long id, UserSummary author, String body, Instant createdAt) {
}
