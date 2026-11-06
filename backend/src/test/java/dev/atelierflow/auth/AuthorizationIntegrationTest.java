package dev.atelierflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.atelierflow.equipment.Criticality;
import dev.atelierflow.equipment.Equipment;
import dev.atelierflow.support.IntegrationTest;
import dev.atelierflow.user.AppUser;
import dev.atelierflow.user.Role;
import dev.atelierflow.workorder.WorkOrderRepository;
import dev.atelierflow.workorder.WorkOrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Proves the permission model with real tokens through the real filter chain:
 * what is reserved to managers is refused (403) to technicians, and a technician only carries out
 * the work orders assigned to them.
 */
class AuthorizationIntegrationTest extends IntegrationTest {

    @Autowired
    private WorkOrderRepository workOrders;

    private MockMvcTester technician;
    private MockMvcTester otherTechnician;
    private MockMvcTester manager;
    private AppUser technicianUser;
    private Equipment press;

    @BeforeEach
    void setUp() {
        technicianUser = fixtures.user("jdupont", Role.TECHNICIAN);
        technician = as(technicianUser);
        otherTechnician = as(fixtures.user("pmartin", Role.TECHNICIAN));
        manager = as(fixtures.user("chef", Role.MANAGER));
        press = fixtures.equipment("PRS-001", Criticality.HIGH);
    }

    private static MvcTestResult post(MockMvcTester client, String uri, String json) {
        return client.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    /** Creates a corrective work order as a manager and returns its id. */
    private long breakdown() {
        MvcTestResult result = post(manager, "/api/work-orders", """
                {"type": "CORRECTIVE", "equipmentId": %d, "title": "Bruit anormal"}
                """.formatted(press.getId()));
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return Long.parseLong(result.getResponse().getHeader("Location").replaceAll(".*/", ""));
    }

    private long plannedForTechnician() {
        long id = breakdown();
        assertThat(post(manager, "/api/work-orders/%d/plan".formatted(id), """
                {"assigneeId": %d, "scheduledFor": "2026-11-20"}
                """.formatted(technicianUser.getId()))).hasStatusOk();
        return id;
    }

    private static void assertForbidden(MvcTestResult result, String code) {
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    @Nested
    class TechnicianCannotDoWhatIsReservedToManagers {

        @Test
        void cannotPlanOrAssignAWorkOrder() {
            long id = breakdown();

            assertForbidden(post(technician, "/api/work-orders/%d/plan".formatted(id), """
                    {"assigneeId": %d, "scheduledFor": "2026-11-20"}
                    """.formatted(technicianUser.getId())), "ACCESS_DENIED");
            assertThat(workOrders.findById(id)).get().extracting("status").isEqualTo(WorkOrderStatus.OPEN);
        }

        @Test
        void cannotUnplanCancelOrEditAWorkOrder() {
            long id = plannedForTechnician();

            assertForbidden(post(technician, "/api/work-orders/%d/unplan".formatted(id), ""), "ACCESS_DENIED");
            assertForbidden(post(technician, "/api/work-orders/%d/cancel".formatted(id), """
                    {"reason": "Je ne veux pas"}
                    """), "ACCESS_DENIED");
            assertForbidden(technician.put().uri("/api/work-orders/{id}", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"title": "Autre", "priority": "LOW"}
                            """).exchange(), "ACCESS_DENIED");
            assertThat(workOrders.findById(id)).get().extracting("status").isEqualTo(WorkOrderStatus.PLANNED);
        }

        @Test
        void cannotCreateAPreventiveWorkOrder() {
            assertForbidden(post(technician, "/api/work-orders", """
                    {"type": "PREVENTIVE", "equipmentId": %d, "title": "Graissage"}
                    """.formatted(press.getId())), "PREVENTIVE_RESERVED");
            assertThat(workOrders.count()).isZero();
        }

        @Test
        void cannotEditTheFleet() {
            assertForbidden(post(technician, "/api/equipment", """
                    {"code": "NEW-01", "name": "Nouvelle", "workshop": "Usinage", "criticality": "LOW"}
                    """), "ACCESS_DENIED");
            assertForbidden(technician.put().uri("/api/equipment/{id}", press.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name": "Renommée", "workshop": "Usinage", "criticality": "LOW"}
                            """).exchange(), "ACCESS_DENIED");
            assertForbidden(technician.delete().uri("/api/equipment/{id}", press.getId()).exchange(), "ACCESS_DENIED");
        }
    }

    @Nested
    class TechnicianWorksOnTheirOwnWorkOrders {

        @Test
        void canReadTheBoardAndReportABreakdown() {
            assertThat(technician.get().uri("/api/work-orders/board")).hasStatusOk();
            assertThat(technician.get().uri("/api/equipment")).hasStatusOk();
            assertThat(post(technician, "/api/work-orders", """
                    {"type": "CORRECTIVE", "equipmentId": %d, "title": "Fuite"}
                    """.formatted(press.getId()))).hasStatus(HttpStatus.CREATED);
        }

        @Test
        void canStartAndCompleteTheirAssignedWorkOrder() {
            long id = plannedForTechnician();

            assertThat(post(technician, "/api/work-orders/%d/start".formatted(id), "")).hasStatusOk();
            assertThat(post(technician, "/api/work-orders/%d/complete".formatted(id), """
                    {"timeSpentMinutes": 40, "resolution": "Roulement remplacé"}
                    """)).hasStatusOk();
        }

        @Test
        void cannotStartOrCompleteAWorkOrderAssignedToSomeoneElse() {
            long id = plannedForTechnician();

            assertForbidden(post(otherTechnician, "/api/work-orders/%d/start".formatted(id), ""), "NOT_ASSIGNEE");

            post(technician, "/api/work-orders/%d/start".formatted(id), "");
            assertForbidden(post(otherTechnician, "/api/work-orders/%d/complete".formatted(id), """
                    {"timeSpentMinutes": 40, "resolution": "Fait"}
                    """), "NOT_ASSIGNEE");
            assertThat(workOrders.findById(id)).get().extracting("status").isEqualTo(WorkOrderStatus.IN_PROGRESS);
        }

        @Test
        void cannotStartAnUnassignedWorkOrder() {
            long id = breakdown();

            assertForbidden(post(technician, "/api/work-orders/%d/start".formatted(id), ""), "NOT_ASSIGNEE");
        }

        @Test
        void canCommentAnyWorkOrderAndIsRecordedAsAuthor() {
            long id = breakdown();

            MvcTestResult result = post(technician, "/api/work-orders/%d/comments".formatted(id), """
                    {"body": "Pièce commandée, livraison jeudi"}
                    """);

            assertThat(result).hasStatus(HttpStatus.CREATED);
            assertThat(result).bodyJson().extractingPath("$.author.username").isEqualTo("jdupont");
            assertThat(technician.get().uri("/api/work-orders/{id}/comments", id))
                    .bodyJson().extractingPath("$[*].body").asArray().containsExactly("Pièce commandée, livraison jeudi");
        }
    }

    @Nested
    class ManagersAndAdmins {

        @Test
        void managerPlansAndCancelsButCannotDeleteAMachine() {
            long id = breakdown();
            assertThat(post(manager, "/api/work-orders/%d/cancel".formatted(id), """
                    {"reason": "Doublon"}
                    """)).hasStatusOk();
            assertThat(post(manager, "/api/equipment", """
                    {"code": "NEW-01", "name": "Nouvelle", "workshop": "Usinage", "criticality": "LOW"}
                    """)).hasStatus(HttpStatus.CREATED);

            assertForbidden(manager.delete().uri("/api/equipment/{id}", press.getId()).exchange(), "ACCESS_DENIED");
        }

        @Test
        void managerMayCarryOutAnyWorkOrder() {
            long id = plannedForTechnician();

            assertThat(post(manager, "/api/work-orders/%d/start".formatted(id), "")).hasStatusOk();
        }

        @Test
        void commentsOfUnknownWorkOrderAreNotFound() {
            assertThat(technician.get().uri("/api/work-orders/999/comments")).hasStatus(HttpStatus.NOT_FOUND);
        }
    }
}
