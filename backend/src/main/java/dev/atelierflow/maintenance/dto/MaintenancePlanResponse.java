package dev.atelierflow.maintenance.dto;

import dev.atelierflow.maintenance.TriggerType;
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
        String instructions,
        TriggerType trigger,
        Integer intervalDays,
        Integer intervalHours,
        LocalDate lastServiceDate,
        double lastServiceHours,
        LocalDate nextDueDate,
        Double remainingHours,
        boolean due,
        boolean active) {
}
