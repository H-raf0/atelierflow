package dev.atelierflow.maintenance.dto;

import dev.atelierflow.maintenance.TriggerType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * @param nextDueDate    calendar plans only
 * @param remainingHours hour-based plans only; negative when overdue
 * @param due            whether the next scheduler run will generate a work order (unless one is pending)
 */
public record MaintenancePlanResponse(
        Long id,
        Long equipmentId,
        String equipmentCode,
        String equipmentName,
        String title,
        @Schema(nullable = true) String instructions,
        TriggerType trigger,
        @Schema(nullable = true) Integer intervalDays,
        @Schema(nullable = true) Integer intervalHours,
        LocalDate lastServiceDate,
        double lastServiceHours,
        @Schema(nullable = true) LocalDate nextDueDate,
        @Schema(nullable = true) Double remainingHours,
        boolean due,
        boolean active) {
}
