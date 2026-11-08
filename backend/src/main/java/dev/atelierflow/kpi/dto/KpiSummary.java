package dev.atelierflow.kpi.dto;

import dev.atelierflow.workorder.Priority;
import java.time.LocalDate;
import java.util.Map;

/**
 * Fleet-wide reliability over a period. Durations are in hours; ratios between 0 and 1.
 *
 * @param mtbfHours       null when no failure occurred (infinite MTBF)
 * @param mttrHours       null when no repair was completed
 * @param preventiveShare share of preventive work among completed work orders; null if none completed
 */
public record KpiSummary(
        LocalDate from,
        LocalDate to,
        int equipmentCount,
        long failures,
        long repairs,
        double downtimeHours,
        Double mtbfHours,
        Double mttrHours,
        double availability,
        Double preventiveShare,
        Map<Priority, Long> backlogByPriority,
        long backlogTotal) {
}
