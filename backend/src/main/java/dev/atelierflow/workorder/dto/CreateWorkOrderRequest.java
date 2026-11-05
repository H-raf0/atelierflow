package dev.atelierflow.workorder.dto;

import dev.atelierflow.workorder.Priority;
import dev.atelierflow.workorder.WorkOrderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param priority optional: for a breakdown it defaults to the criticality of the machine
 */
public record CreateWorkOrderRequest(
        @NotNull WorkOrderType type,
        @NotNull Long equipmentId,
        @NotBlank @Size(max = 150) String title,
        @Size(max = 4000) String description,
        Priority priority) {
}
