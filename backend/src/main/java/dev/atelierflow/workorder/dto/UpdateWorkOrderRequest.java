package dev.atelierflow.workorder.dto;

import dev.atelierflow.workorder.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateWorkOrderRequest(
        @NotBlank @Size(max = 150) String title,
        @Size(max = 4000) String description,
        @NotNull Priority priority) {
}
