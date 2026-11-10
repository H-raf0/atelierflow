package dev.atelierflow.demo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.atelierflow.TestcontainersConfiguration;
import dev.atelierflow.auth.AuthService;
import dev.atelierflow.kpi.KpiService;
import dev.atelierflow.kpi.dto.KpiSummary;
import dev.atelierflow.workorder.WorkOrderHistoryService;
import dev.atelierflow.workorder.dto.HistoryEntry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** The demo profile loads a consistent data set at startup (the application context is started with it). */
@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
class DemoDataLoaderIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DemoDataLoader loader;

    @Autowired
    private AuthService authService;

    @Autowired
    private KpiService kpiService;

    @Autowired
    private WorkOrderHistoryService historyService;

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    @Test
    void loadsAWorkshopWithSixMonthsOfHistory() {
        assertThat(count("SELECT COUNT(*) FROM equipment")).isEqualTo(20);
        assertThat(count("SELECT COUNT(*) FROM app_user")).isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM work_order")).isBetween(100L, 160L);
        assertThat(count("SELECT COUNT(*) FROM work_order WHERE status IN ('OPEN', 'PLANNED', 'IN_PROGRESS')")).isPositive();
        assertThat(count("SELECT COUNT(*) FROM maintenance_plan")).isPositive();
        // a machine is down exactly when it has an unfinished breakdown
        assertThat(count("""
                SELECT COUNT(*) FROM equipment e WHERE (e.status = 'DOWN') <> EXISTS (
                    SELECT 1 FROM work_order w WHERE w.equipment_id = e.id AND w.type = 'CORRECTIVE'
                      AND w.status IN ('OPEN', 'PLANNED', 'IN_PROGRESS'))
                """)).isZero();
        assertThat(count("SELECT COUNT(*) FROM work_order WHERE reported_at > now() OR completed_at > now()")).isZero();
    }

    @Test
    void indicatorsAreMeaningful() {
        KpiSummary summary = kpiService.summary(null, null);

        assertThat(summary.failures()).isGreaterThan(20);
        assertThat(summary.mtbfHours()).isNotNull().isPositive();
        assertThat(summary.mttrHours()).isNotNull().isBetween(0.5, 48.0);
        assertThat(summary.availability()).isBetween(0.9, 1.0);
        assertThat(summary.preventiveShare()).isNotNull().isBetween(0.1, 0.9);
    }

    @Test
    void workOrdersComeWithAConsistentAuditTrail() {
        Long completed = jdbc.queryForObject(
                "SELECT id FROM work_order WHERE status = 'DONE' AND type = 'CORRECTIVE' ORDER BY id LIMIT 1", Long.class);

        List<HistoryEntry> history = historyService.history(completed);

        assertThat(history).extracting(HistoryEntry::action).containsExactly("CREATED", "UPDATED", "UPDATED", "UPDATED");
        assertThat(history.get(1).username()).isEqualTo("responsable");
        assertThat(history).extracting(HistoryEntry::timestamp).isSorted();
        assertThat(history.getLast().changes()).anySatisfy(change -> assertThat(change.to()).isEqualTo("DONE"));
    }

    @Test
    void theThreeDocumentedAccountsCanSignIn() {
        for (String username : List.of("admin", "responsable", "technicien")) {
            assertThat(authService.login(username, DemoDataLoader.DEMO_PASSWORD).accessToken()).isNotBlank();
        }
    }

    @Test
    void doesNotLoadTwice() {
        long before = count("SELECT COUNT(*) FROM work_order");

        loader.run(new DefaultApplicationArguments());

        assertThat(count("SELECT COUNT(*) FROM work_order")).isEqualTo(before);
    }
}
