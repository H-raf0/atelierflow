package dev.atelierflow.workorder;

import static org.assertj.core.api.Assertions.assertThat;

import dev.atelierflow.equipment.Criticality;
import dev.atelierflow.equipment.Equipment;
import dev.atelierflow.support.IntegrationTest;
import dev.atelierflow.user.AppUser;
import dev.atelierflow.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class WorkOrderHistoryIntegrationTest extends IntegrationTest {

    @Autowired
    private WorkOrderService workOrderService;

    private MockMvcTester manager;
    private MockMvcTester technician;
    private AppUser technicianUser;
    private Equipment press;

    @BeforeEach
    void setUp() {
        manager = as(fixtures.user("chef", Role.MANAGER));
        technicianUser = fixtures.user("jdupont", Role.TECHNICIAN);
        technician = as(technicianUser);
        press = fixtures.equipment("PRS-001", Criticality.HIGH);
    }

    private static MvcTestResult post(MockMvcTester client, String uri, String json) {
        return client.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    @Test
    void recordsWhoChangedWhatAtEachStep() {
        MvcTestResult created = post(manager, "/api/work-orders", """
                {"type": "CORRECTIVE", "equipmentId": %d, "title": "Fuite vérin"}
                """.formatted(press.getId()));
        long id = Long.parseLong(created.getResponse().getHeader("Location").replaceAll(".*/", ""));
        post(manager, "/api/work-orders/%d/plan".formatted(id), """
                {"assigneeId": %d, "scheduledFor": "2026-11-20"}
                """.formatted(technicianUser.getId()));
        post(technician, "/api/work-orders/%d/start".formatted(id), "");
        post(technician, "/api/work-orders/%d/complete".formatted(id), """
                {"timeSpentMinutes": 50, "resolution": "Joint changé"}
                """);

        MvcTestResult history = mvc.get().uri("/api/work-orders/{id}/history", id).exchange();

        assertThat(history).hasStatusOk();
        assertThat(history).bodyJson().extractingPath("$[*].username").asArray()
                .containsExactly("chef", "chef", "jdupont", "jdupont");
        assertThat(history).bodyJson().extractingPath("$[*].action").asArray()
                .containsExactly("CREATED", "UPDATED", "UPDATED", "UPDATED");
        assertThat(history).bodyJson().extractingPath("$[0].changes[?(@.field == 'status')].to").asArray()
                .containsExactly("OPEN");
        assertThat(history).bodyJson().extractingPath("$[1].changes[*].field").asArray()
                .containsExactly("status", "assignee", "scheduledFor");
        assertThat(history).bodyJson().extractingPath("$[1].changes[1].to").isEqualTo("User jdupont");
        assertThat(history).bodyJson().extractingPath("$[2].changes[*].field").asArray().containsExactly("status");
        assertThat(history).bodyJson().extractingPath("$[3].changes[*].field").asArray()
                .containsExactly("status", "timeSpentMinutes", "resolution");
        assertThat(history).bodyJson().extractingPath("$[3].changes[0].from").isEqualTo("IN_PROGRESS");
        assertThat(history).bodyJson().extractingPath("$[3].changes[0].to").isEqualTo("DONE");
    }

    @Test
    void changesMadeByBackgroundJobsAreAttributedToSystem() {
        Long id = workOrderService.reportBreakdown(press.getId(), "Surchauffe moteur", null).id();

        assertThat(mvc.get().uri("/api/work-orders/{id}/history", id))
                .bodyJson().extractingPath("$[0].username").isEqualTo("system");
    }

    @Test
    void historyOfUnknownWorkOrderIsNotFound() {
        assertThat(mvc.get().uri("/api/work-orders/999/history")).hasStatus(HttpStatus.NOT_FOUND);
    }
}
