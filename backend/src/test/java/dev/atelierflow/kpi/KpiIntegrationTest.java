package dev.atelierflow.kpi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.atelierflow.equipment.Criticality;
import dev.atelierflow.equipment.Equipment;
import dev.atelierflow.support.IntegrationTest;
import dev.atelierflow.user.AppUser;
import dev.atelierflow.user.Role;
import dev.atelierflow.workorder.Priority;
import dev.atelierflow.workorder.WorkOrder;
import dev.atelierflow.workorder.WorkOrderRepository;
import dev.atelierflow.workorder.WorkOrderType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * KPIs computed on a hand-checked data set. Period: 2026-10-01 → 2026-10-10 = 10 days = 240 h per machine.
 *
 * <pre>
 * PRS-001  failure 1: 10-02 08:00 → 12:00            downtime 4 h, repair 4 h
 *          failure 2: 10-05 10:00 → 16:00            downtime 6 h, repair 6 h
 *          failure 0: 09-30 20:00 → 10-01 04:00      reported before the period: downtime 4 h inside it,
 *                                                     not a new failure, repair (8 h) completed in the period
 *          cancelled corrective                       ignored (false alarm)
 * TOUR-01  failure  : 10-10 20:00 → still open       downtime 4 h (until the end of the period), no repair
 *          preventive completed 10-03                 counts for the preventive share
 * </pre>
 */
class KpiIntegrationTest extends IntegrationTest {

    private static final String PERIOD = "from=2026-10-01&to=2026-10-10";

    @Autowired
    private WorkOrderRepository workOrders;

    private AppUser technician;
    private Equipment press;
    private Equipment lathe;

    @BeforeEach
    void setUp() {
        technician = fixtures.user("jdupont", Role.TECHNICIAN);
        press = fixtures.equipment("PRS-001", Criticality.CRITICAL);
        lathe = fixtures.equipment("TOUR-01", Criticality.LOW);
    }

    private static Instant at(String dateTime) {
        return Instant.parse(dateTime + ":00Z");
    }

    private void repaired(Equipment equipment, WorkOrderType type, String reported, String completed) {
        WorkOrder workOrder = new WorkOrder(equipment, type, Priority.HIGH, "Intervention", null, at(reported));
        workOrder.plan(technician, LocalDate.parse(reported.substring(0, 10)));
        workOrder.start(at(reported));
        workOrder.complete(60, "OK", at(completed));
        workOrders.save(workOrder);
    }

    private void givenHandCheckedDataSet() {
        repaired(press, WorkOrderType.CORRECTIVE, "2026-10-02T08:00", "2026-10-02T12:00");
        repaired(press, WorkOrderType.CORRECTIVE, "2026-10-05T10:00", "2026-10-05T16:00");
        repaired(press, WorkOrderType.CORRECTIVE, "2026-09-30T20:00", "2026-10-01T04:00");
        WorkOrder falseAlarm = new WorkOrder(press, WorkOrderType.CORRECTIVE, Priority.LOW, "Fausse alerte", null,
                at("2026-10-06T08:00"));
        falseAlarm.cancel("Capteur débranché", at("2026-10-06T08:30"));
        workOrders.save(falseAlarm);

        workOrders.save(new WorkOrder(lathe, WorkOrderType.CORRECTIVE, Priority.MEDIUM, "Bruit", null,
                at("2026-10-10T20:00")));
        repaired(lathe, WorkOrderType.PREVENTIVE, "2026-10-03T08:00", "2026-10-03T09:00");
    }

    @Test
    void fleetSummaryFollowsTheDocumentedFormulas() {
        givenHandCheckedDataSet();

        MvcTestResult result = mvc.get().uri("/api/kpis/summary?" + PERIOD).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.equipmentCount").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.failures").isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$.repairs").isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$.downtimeHours").isEqualTo(18.0);
        // MTBF = (2 × 240 − 18) / 3 ; MTTR = (4 + 6 + 8) / 3 ; availability = 1 − 18 / 480
        assertThat(result).bodyJson().extractingPath("$.mtbfHours").isEqualTo(154.0);
        assertThat(result).bodyJson().extractingPath("$.mttrHours").isEqualTo(6.0);
        assertThat(result).bodyJson().extractingPath("$.availability").isEqualTo(0.9625);
        // completed in the period: 3 corrective + 1 preventive
        assertThat(result).bodyJson().extractingPath("$.preventiveShare").isEqualTo(0.25);
        // backlog = what is still to do today, whatever the period
        assertThat(result).bodyJson().extractingPath("$.backlogTotal").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.backlogByPriority.MEDIUM").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.backlogByPriority.URGENT").isEqualTo(0);
    }

    @Test
    void perMachineIndicatorsAreRankedByDowntime() {
        givenHandCheckedDataSet();

        MvcTestResult result = mvc.get().uri("/api/kpis/equipment?" + PERIOD).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[*].code").asArray().containsExactly("PRS-001", "TOUR-01");
        // PRS-001: MTBF = (240 − 14) / 2, MTTR = 18 / 3, availability = 1 − 14 / 240
        assertThat(result).bodyJson().extractingPath("$[0].downtimeHours").isEqualTo(14.0);
        assertThat(result).bodyJson().extractingPath("$[0].mtbfHours").isEqualTo(113.0);
        assertThat(result).bodyJson().extractingPath("$[0].mttrHours").isEqualTo(6.0);
        assertThat(result).bodyJson().extractingPath("$[0].availability").isEqualTo(0.9417);
        // TOUR-01: one failure still open, so no MTTR yet
        assertThat(result).bodyJson().extractingPath("$[1].mtbfHours").isEqualTo(236.0);
        assertThat(result).bodyJson().extractingPath("$[1].mttrHours").isNull();
        assertThat(result).bodyJson().extractingPath("$[1].availability").isEqualTo(0.9833);
    }

    @Test
    void withoutFailureMtbfAndMttrAreUndefinedAndAvailabilityIsFull() {
        MvcTestResult result = mvc.get().uri("/api/kpis/summary?" + PERIOD).exchange();

        assertThat(result).bodyJson().extractingPath("$.mtbfHours").isNull();
        assertThat(result).bodyJson().extractingPath("$.mttrHours").isNull();
        assertThat(result).bodyJson().extractingPath("$.availability").isEqualTo(1.0);
        assertThat(result).bodyJson().extractingPath("$.preventiveShare").isNull();
    }

    @Test
    void defaultPeriodIsTheLast90Days() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        MvcTestResult result = mvc.get().uri("/api/kpis/summary").exchange();

        assertThat(result).bodyJson().extractingPath("$.to").isEqualTo(today.toString());
        assertThat(result).bodyJson().extractingPath("$.from").isEqualTo(today.minusDays(89).toString());
    }

    @Test
    void monthlyActivityIncludesEmptyMonths() {
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        String previousMonthStart = current.minusMonths(1).atDay(1) + "T00:00";
        repaired(press, WorkOrderType.CORRECTIVE, previousMonthStart, current.minusMonths(1).atDay(1) + "T05:00");
        workOrders.save(new WorkOrder(press, WorkOrderType.PREVENTIVE, Priority.MEDIUM, "Graissage", null,
                current.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant()));

        MvcTestResult result = mvc.get().uri("/api/kpis/monthly?months=3").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[*].month").asArray().containsExactly(
                current.minusMonths(2).toString(), current.minusMonths(1).toString(), current.toString());
        assertThat(result).bodyJson().extractingPath("$[0].corrective").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$[1].corrective").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$[1].downtimeHours").isEqualTo(5.0);
        assertThat(result).bodyJson().extractingPath("$[2].preventive").isEqualTo(1);
    }

    @Test
    void invalidPeriodsAreRejected() {
        assertThat(mvc.get().uri("/api/kpis/summary?from=2026-10-10&to=2026-10-01"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_PERIOD");
        assertThat(mvc.get().uri("/api/kpis/monthly?months=0")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void techniciansCanReadTheDashboard() {
        assertThat(as(technician).get().uri("/api/kpis/summary")).hasStatusOk();
    }
}
