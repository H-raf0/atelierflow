package dev.atelierflow.kpi;

import dev.atelierflow.common.error.BadRequestException;
import dev.atelierflow.kpi.KpiRepository.EquipmentReliabilityRow;
import dev.atelierflow.kpi.KpiRepository.MonthlyRow;
import dev.atelierflow.kpi.dto.EquipmentKpi;
import dev.atelierflow.kpi.dto.KpiSummary;
import dev.atelierflow.kpi.dto.MonthlyKpi;
import dev.atelierflow.workorder.Priority;
import dev.atelierflow.workorder.WorkOrderType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintenance reliability indicators.
 *
 * <p>Definitions, over a period of length {@code T} hours and for {@code N} machines (the
 * fleet-wide figures add up the machines):
 * <ul>
 *   <li><b>Failure</b>: a corrective work order (cancelled ones are false alarms and ignored).</li>
 *   <li><b>Downtime</b> of a failure: from its report to the end of the repair (work order closed),
 *       or to the end of the period if not repaired yet, clipped to the period. This is the time
 *       production is actually lost, waiting for a technician included.</li>
 *   <li><b>Uptime</b> = {@code N × T − downtime}.</li>
 *   <li><b>MTBF</b> (Mean Time Between Failures) = uptime / number of failures reported in the period.
 *       The higher, the more reliable the machine. Undefined (null) without failure.</li>
 *   <li><b>MTTR</b> (Mean Time To Repair, here "to restore") = total duration (report → closure) of the
 *       repairs completed in the period / number of these repairs. The lower, the more reactive the
 *       maintenance team. Undefined (null) without completed repair.</li>
 *   <li><b>Availability</b> = uptime / {@code (N × T)}, i.e. the share of time the machines could
 *       produce. With steady failures it matches the classic {@code MTBF / (MTBF + MTTR)}.</li>
 * </ul>
 * Simplification: every machine of the fleet is assumed to exist during the whole period.
 */
@Service
@Transactional(readOnly = true)
public class KpiService {

    static final int DEFAULT_PERIOD_DAYS = 90;

    private final KpiRepository repository;
    private final Clock clock;

    public KpiService(KpiRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Resolved period: whole days in UTC, the end being capped to now (the future has no downtime). */
    record Period(LocalDate fromDate, LocalDate toDate, Instant from, Instant to) {

        double hours() {
            return Duration.between(from, to).toSeconds() / 3600.0;
        }
    }

    public KpiSummary summary(LocalDate from, LocalDate to) {
        Period period = period(from, to);
        List<EquipmentReliabilityRow> rows = repository.equipmentReliability(period.from(), period.to());

        long failures = rows.stream().mapToLong(EquipmentReliabilityRow::failures).sum();
        long repairs = rows.stream().mapToLong(EquipmentReliabilityRow::repairs).sum();
        double downtime = rows.stream().mapToDouble(EquipmentReliabilityRow::downtimeHours).sum();
        double repairHours = rows.stream().mapToDouble(EquipmentReliabilityRow::repairHours).sum();
        double scheduledHours = rows.size() * period.hours();

        Map<WorkOrderType, Long> completed = repository.completedByType(period.from(), period.to());
        long completedTotal = completed.values().stream().mapToLong(Long::longValue).sum();
        Map<Priority, Long> backlog = repository.backlogByPriority();

        return new KpiSummary(
                period.fromDate(), period.toDate(), rows.size(), failures, repairs,
                round(downtime, 1),
                mtbf(scheduledHours, downtime, failures),
                mttr(repairHours, repairs),
                availability(scheduledHours, downtime),
                completedTotal == 0 ? null : round((double) completed.get(WorkOrderType.PREVENTIVE) / completedTotal, 3),
                backlog,
                backlog.values().stream().mapToLong(Long::longValue).sum());
    }

    /** Machines ranked by downtime, the worst first (Pareto of the maintenance effort). */
    public List<EquipmentKpi> byEquipment(LocalDate from, LocalDate to) {
        Period period = period(from, to);
        double hours = period.hours();
        return repository.equipmentReliability(period.from(), period.to()).stream()
                .map(row -> new EquipmentKpi(row.id(), row.code(), row.name(), row.workshop(),
                        row.failures(), row.repairs(), round(row.downtimeHours(), 1),
                        mtbf(hours, row.downtimeHours(), row.failures()),
                        mttr(row.repairHours(), row.repairs()),
                        availability(hours, row.downtimeHours())))
                .toList();
    }

    /** Corrective vs preventive activity of the last {@code months} months, including empty months. */
    public List<MonthlyKpi> monthly(int months) {
        YearMonth current = YearMonth.now(clock);
        YearMonth first = current.minusMonths(months - 1L);
        Instant since = first.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<String, MonthlyRow> rows = repository.monthly(since).stream()
                .collect(Collectors.toMap(MonthlyRow::month, Function.identity()));

        List<MonthlyKpi> result = new ArrayList<>();
        for (YearMonth month = first; !month.isAfter(current); month = month.plusMonths(1)) {
            MonthlyRow row = rows.get(month.toString());
            result.add(row == null
                    ? new MonthlyKpi(month.toString(), 0, 0, 0)
                    : new MonthlyKpi(row.month(), row.corrective(), row.preventive(), round(row.downtimeHours(), 1)));
        }
        return result;
    }

    Period period(LocalDate from, LocalDate to) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        LocalDate toDate = to != null ? to : today;
        LocalDate fromDate = from != null ? from : toDate.minusDays(DEFAULT_PERIOD_DAYS - 1L);
        if (fromDate.isAfter(toDate) || fromDate.isAfter(today)) {
            throw new BadRequestException("INVALID_PERIOD", "The period must start before its end and not in the future");
        }
        Instant start = fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new Period(fromDate, toDate, start, end.isAfter(now) ? now : end);
    }

    private static Double mtbf(double scheduledHours, double downtimeHours, long failures) {
        return failures == 0 ? null : round((scheduledHours - downtimeHours) / failures, 1);
    }

    private static Double mttr(double repairHours, long repairs) {
        return repairs == 0 ? null : round(repairHours / repairs, 1);
    }

    private static double availability(double scheduledHours, double downtimeHours) {
        return scheduledHours == 0 ? 1.0 : round(1 - downtimeHours / scheduledHours, 4);
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
