package dev.atelierflow.kpi;

import dev.atelierflow.workorder.Priority;
import dev.atelierflow.workorder.WorkOrderType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reliability aggregates computed by PostgreSQL in a few set-based queries (one pass over the work
 * orders) instead of loading entities and looping in Java.
 *
 * <p>Plain SQL through {@link NamedParameterJdbcTemplate}: these are reporting queries returning
 * aggregates, not entities, so JPA brings nothing here and SQL keeps them readable.
 */
@Repository
class KpiRepository {

    /**
     * Per machine, over [from, to): corrective work orders whose downtime overlaps the period.
     * Downtime of a breakdown = reported → completed (still running → until {@code to}), clipped to the period.
     *
     * <p>The {@code FILTER (WHERE w.id IS NOT NULL)} matters: PostgreSQL's GREATEST/LEAST ignore NULLs,
     * so without it the empty row of a machine without breakdown would count the whole period as downtime.
     */
    private static final String EQUIPMENT_RELIABILITY = """
            SELECT e.id, e.code, e.name, e.workshop,
                   COUNT(w.id) FILTER (WHERE w.reported_at >= :from) AS failures,
                   COALESCE(SUM(EXTRACT(EPOCH FROM LEAST(COALESCE(w.completed_at, :to), :to)
                                                  - GREATEST(w.reported_at, :from)))
                            FILTER (WHERE w.id IS NOT NULL), 0) / 3600.0 AS downtime_hours,
                   COUNT(w.id) FILTER (WHERE w.completed_at >= :from AND w.completed_at < :to) AS repairs,
                   COALESCE(SUM(EXTRACT(EPOCH FROM w.completed_at - w.reported_at))
                            FILTER (WHERE w.completed_at >= :from AND w.completed_at < :to), 0) / 3600.0 AS repair_hours
            FROM equipment e
            LEFT JOIN work_order w
                   ON w.equipment_id = e.id
                  AND w.type = 'CORRECTIVE'
                  AND w.status <> 'CANCELLED'
                  AND w.reported_at < :to
                  AND COALESCE(w.completed_at, :to) > :from
            GROUP BY e.id, e.code, e.name, e.workshop
            ORDER BY downtime_hours DESC, e.code
            """;

    private static final String BACKLOG_BY_PRIORITY = """
            SELECT priority, COUNT(*) AS total
            FROM work_order
            WHERE status IN ('OPEN', 'PLANNED', 'IN_PROGRESS')
            GROUP BY priority
            """;

    private static final String COMPLETED_BY_TYPE = """
            SELECT type, COUNT(*) AS total
            FROM work_order
            WHERE status = 'DONE' AND completed_at >= :from AND completed_at < :to
            GROUP BY type
            """;

    private static final String MONTHLY = """
            SELECT to_char(date_trunc('month', reported_at AT TIME ZONE 'UTC'), 'YYYY-MM') AS month,
                   COUNT(*) FILTER (WHERE type = 'CORRECTIVE') AS corrective,
                   COUNT(*) FILTER (WHERE type = 'PREVENTIVE') AS preventive,
                   COALESCE(SUM(EXTRACT(EPOCH FROM completed_at - reported_at))
                            FILTER (WHERE type = 'CORRECTIVE' AND completed_at IS NOT NULL), 0) / 3600.0 AS downtime_hours
            FROM work_order
            WHERE status <> 'CANCELLED' AND reported_at >= :since
            GROUP BY 1
            ORDER BY 1
            """;

    private final NamedParameterJdbcTemplate jdbc;

    KpiRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record EquipmentReliabilityRow(Long id, String code, String name, String workshop, long failures,
                                   double downtimeHours, long repairs, double repairHours) {
    }

    record MonthlyRow(String month, long corrective, long preventive, double downtimeHours) {
    }

    List<EquipmentReliabilityRow> equipmentReliability(Instant from, Instant to) {
        return jdbc.query(EQUIPMENT_RELIABILITY, period(from, to), (rs, i) -> new EquipmentReliabilityRow(
                rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("workshop"),
                rs.getLong("failures"), rs.getDouble("downtime_hours"),
                rs.getLong("repairs"), rs.getDouble("repair_hours")));
    }

    Map<Priority, Long> backlogByPriority() {
        Map<Priority, Long> backlog = new EnumMap<>(Priority.class);
        for (Priority priority : Priority.values()) {
            backlog.put(priority, 0L);
        }
        jdbc.query(BACKLOG_BY_PRIORITY, rs -> {
            backlog.put(Priority.valueOf(rs.getString("priority")), rs.getLong("total"));
        });
        return backlog;
    }

    Map<WorkOrderType, Long> completedByType(Instant from, Instant to) {
        Map<WorkOrderType, Long> completed = new EnumMap<>(WorkOrderType.class);
        for (WorkOrderType type : WorkOrderType.values()) {
            completed.put(type, 0L);
        }
        jdbc.query(COMPLETED_BY_TYPE, period(from, to), rs -> {
            completed.put(WorkOrderType.valueOf(rs.getString("type")), rs.getLong("total"));
        });
        return completed;
    }

    List<MonthlyRow> monthly(Instant since) {
        return jdbc.query(MONTHLY, new MapSqlParameterSource("since", utc(since)), (rs, i) -> new MonthlyRow(
                rs.getString("month"), rs.getLong("corrective"), rs.getLong("preventive"),
                rs.getDouble("downtime_hours")));
    }

    private static MapSqlParameterSource period(Instant from, Instant to) {
        return new MapSqlParameterSource().addValue("from", utc(from)).addValue("to", utc(to));
    }

    /** The PostgreSQL driver maps OffsetDateTime (not Instant) to TIMESTAMPTZ. */
    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
