package dev.atelierflow.kpi.dto;

/** Reliability of one machine over the period (same definitions as {@link KpiSummary}). */
public record EquipmentKpi(
        Long equipmentId,
        String code,
        String name,
        String workshop,
        long failures,
        long repairs,
        double downtimeHours,
        Double mtbfHours,
        Double mttrHours,
        double availability) {
}
