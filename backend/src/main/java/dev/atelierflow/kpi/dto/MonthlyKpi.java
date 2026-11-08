package dev.atelierflow.kpi.dto;

/**
 * Activity of one month (by report date).
 *
 * @param month         {@code yyyy-MM}
 * @param downtimeHours restore time of the breakdowns reported that month and already repaired
 */
public record MonthlyKpi(String month, long corrective, long preventive, double downtimeHours) {
}
