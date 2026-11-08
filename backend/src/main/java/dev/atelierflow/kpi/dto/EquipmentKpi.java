package dev.atelierflow.kpi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Reliability of one machine over the period (same definitions as {@link KpiSummary}). */
public record EquipmentKpi(
        Long equipmentId,
        String code,
        String name,
        String workshop,
        long failures,
        long repairs,
        double downtimeHours,
        @Schema(nullable = true) Double mtbfHours,
        @Schema(nullable = true) Double mttrHours,
        double availability) {
}
