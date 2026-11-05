package dev.atelierflow.workorder;

import static org.assertj.core.api.Assertions.assertThat;

import dev.atelierflow.equipment.Criticality;
import dev.atelierflow.equipment.Equipment;
import dev.atelierflow.equipment.EquipmentRepository;
import dev.atelierflow.equipment.EquipmentStatus;
import dev.atelierflow.support.IntegrationTest;
import dev.atelierflow.support.SqlStatistics;
import dev.atelierflow.user.AppUser;
import dev.atelierflow.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonPathValueAssert;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class WorkOrderControllerIntegrationTest extends IntegrationTest {

    @Autowired
    private EquipmentRepository equipments;

    @Autowired
    private WorkOrderRepository workOrders;

    @Autowired
    private SqlStatistics sql;

    private Equipment press;
    private AppUser technician;

    @BeforeEach
    void setUp() {
        press = fixtures.equipment("PRS-001", Criticality.CRITICAL);
        technician = fixtures.user("jdupont", Role.TECHNICIAN);
    }

    private long createWorkOrder(WorkOrderType type, Equipment equipment) {
        MvcTestResult result = mvc.post().uri("/api/work-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type": "%s", "equipmentId": %d, "title": "Fuite d'huile vérin principal"}
                        """.formatted(type, equipment.getId()))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return Long.parseLong(result.getResponse().getHeader("Location").replaceAll(".*/", ""));
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult plan(long id) {
        return post("/api/work-orders/%d/plan".formatted(id),
                """
                {"assigneeId": %d, "scheduledFor": "2026-11-20"}
                """.formatted(technician.getId()));
    }

    private MvcTestResult complete(long id) {
        return post("/api/work-orders/%d/complete".formatted(id),
                """
                {"timeSpentMinutes": 75, "resolution": "Joint de tige remplacé, essais OK"}
                """);
    }

    private JsonPathValueAssert json(MvcTestResult result, String path) {
        return assertThat(result).bodyJson().extractingPath(path);
    }

    private EquipmentStatus pressStatus() {
        return equipments.findById(press.getId()).orElseThrow().getStatus();
    }

    @Nested
    class Lifecycle {

        @Test
        void correctiveWorkOrderGoesThroughTheWholeLifecycle() {
            long id = createWorkOrder(WorkOrderType.CORRECTIVE, press);
            assertThat(pressStatus()).isEqualTo(EquipmentStatus.DOWN);

            MvcTestResult planned = plan(id);
            assertThat(planned).hasStatusOk();
            json(planned, "$.status").isEqualTo("PLANNED");
            json(planned, "$.assignee.username").isEqualTo("jdupont");
            json(planned, "$.allowedTransitions").asArray()
                    .containsExactlyInAnyOrder("OPEN", "IN_PROGRESS", "CANCELLED");

            MvcTestResult started = post("/api/work-orders/%d/start".formatted(id), "");
            json(started, "$.status").isEqualTo("IN_PROGRESS");
            json(started, "$.startedAt").isNotNull();
            assertThat(pressStatus()).isEqualTo(EquipmentStatus.DOWN);

            MvcTestResult done = complete(id);
            assertThat(done).hasStatusOk();
            json(done, "$.status").isEqualTo("DONE");
            json(done, "$.timeSpentMinutes").isEqualTo(75);
            json(done, "$.allowedTransitions").asArray().isEmpty();
            assertThat(pressStatus()).isEqualTo(EquipmentStatus.OPERATIONAL);
        }

        @Test
        void breakdownOnCriticalMachineIsUrgentByDefault() {
            long id = createWorkOrder(WorkOrderType.CORRECTIVE, press);

            MvcTestResult result = mvc.get().uri("/api/work-orders/{id}", id).exchange();
            json(result, "$.priority").isEqualTo("URGENT");
            json(result, "$.reference").isEqualTo("OT-%05d".formatted(id));
            json(result, "$.equipmentCriticality").isEqualTo("CRITICAL");
        }

        @Test
        void preventiveInterventionPutsMachineInMaintenanceWhileInProgress() {
            long id = createWorkOrder(WorkOrderType.PREVENTIVE, press);
            assertThat(pressStatus()).isEqualTo(EquipmentStatus.OPERATIONAL);

            plan(id);
            post("/api/work-orders/%d/start".formatted(id), "");
            assertThat(pressStatus()).isEqualTo(EquipmentStatus.IN_MAINTENANCE);

            complete(id);
            assertThat(pressStatus()).isEqualTo(EquipmentStatus.OPERATIONAL);
        }

        @Test
        void machineStaysDownWhileAnotherBreakdownIsOpen() {
            long first = createWorkOrder(WorkOrderType.CORRECTIVE, press);
            long second = createWorkOrder(WorkOrderType.CORRECTIVE, press);

            post("/api/work-orders/%d/cancel".formatted(first), """
                    {"reason": "Doublon"}
                    """);
            assertThat(pressStatus()).isEqualTo(EquipmentStatus.DOWN);

            post("/api/work-orders/%d/cancel".formatted(second), """
                    {"reason": "Fausse alerte"}
                    """);
            assertThat(pressStatus()).isEqualTo(EquipmentStatus.OPERATIONAL);
        }

        @Test
        void unplanReturnsTheWorkOrderToOpen() {
            long id = createWorkOrder(WorkOrderType.CORRECTIVE, press);
            plan(id);

            MvcTestResult result = post("/api/work-orders/%d/unplan".formatted(id), "");

            json(result, "$.status").isEqualTo("OPEN");
            json(result, "$.assignee").isNull();
        }
    }

    @Nested
    class Rules {

        @Test
        void invalidTransitionIsRejectedWithConflict() {
            long id = createWorkOrder(WorkOrderType.CORRECTIVE, press);

            MvcTestResult result = post("/api/work-orders/%d/start".formatted(id), "");

            assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
            json(result, "$.code").isEqualTo("INVALID_TRANSITION");
            json(result, "$.detail").isEqualTo("A work order cannot go from OPEN to IN_PROGRESS");
            assertThat(workOrders.findById(id)).get().extracting(WorkOrder::getStatus)
                    .isEqualTo(WorkOrderStatus.OPEN);
        }

        @Test
        void startedWorkCannotBeCancelled() {
            long id = createWorkOrder(WorkOrderType.CORRECTIVE, press);
            plan(id);
            post("/api/work-orders/%d/start".formatted(id), "");

            MvcTestResult result = post("/api/work-orders/%d/cancel".formatted(id), """
                    {"reason": "Finalement non"}
                    """);

            assertThat(result).hasStatus(HttpStatus.CONFLICT);
            json(result, "$.code").isEqualTo("INVALID_TRANSITION");
        }

        @Test
        void assigningANonTechnicianIsRejected() {
            AppUser manager = fixtures.user("chef", Role.MANAGER);
            long id = createWorkOrder(WorkOrderType.CORRECTIVE, press);

            MvcTestResult result = post("/api/work-orders/%d/plan".formatted(id), """
                    {"assigneeId": %d, "scheduledFor": "2026-11-20"}
                    """.formatted(manager.getId()));

            assertThat(result).hasStatus(HttpStatus.CONFLICT);
            json(result, "$.code").isEqualTo("ASSIGNEE_NOT_TECHNICIAN");
        }

        @Test
        void completionPayloadIsValidated() {
            long id = createWorkOrder(WorkOrderType.CORRECTIVE, press);

            MvcTestResult result = post("/api/work-orders/%d/complete".formatted(id), """
                    {"timeSpentMinutes": 0, "resolution": ""}
                    """);

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            json(result, "$.violations[*].field").asArray()
                    .containsExactlyInAnyOrder("resolution", "timeSpentMinutes");
        }

        @Test
        void closedWorkOrderCannotBeEdited() {
            long id = createWorkOrder(WorkOrderType.CORRECTIVE, press);
            post("/api/work-orders/%d/cancel".formatted(id), """
                    {"reason": "Doublon"}
                    """);

            assertThat(mvc.put().uri("/api/work-orders/{id}", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"title": "Nouveau titre", "priority": "LOW"}
                            """))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("WORK_ORDER_CLOSED");
        }

        @Test
        void openWorkOrderCanBeEdited() {
            long id = createWorkOrder(WorkOrderType.CORRECTIVE, press);

            assertThat(mvc.put().uri("/api/work-orders/{id}", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"title": "Fuite vérin gauche", "description": "Flaque sous la presse", "priority": "HIGH"}
                            """))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.priority").isEqualTo("HIGH");
        }

        @Test
        void unknownEquipmentOrWorkOrderIsNotFound() {
            assertThat(mvc.post().uri("/api/work-orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"type": "CORRECTIVE", "equipmentId": 999, "title": "Panne"}
                            """))
                    .hasStatus(HttpStatus.NOT_FOUND);
            assertThat(mvc.get().uri("/api/work-orders/999")).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(post("/api/work-orders/999/start", "")).hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void equipmentWithHistoryCannotBeDeleted() {
            createWorkOrder(WorkOrderType.CORRECTIVE, press);

            assertThat(mvc.delete().uri("/api/equipment/{id}", press.getId()))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("EQUIPMENT_HAS_HISTORY");
            assertThat(equipments.existsById(press.getId())).isTrue();
        }
    }

    @Nested
    class Queries {

        @Test
        void boardShowsActiveWorkOrdersMostUrgentFirst() {
            Equipment lathe = fixtures.equipment("TOUR-01", Criticality.LOW);
            long low = createWorkOrder(WorkOrderType.CORRECTIVE, lathe);
            long urgent = createWorkOrder(WorkOrderType.CORRECTIVE, press);
            long cancelled = createWorkOrder(WorkOrderType.CORRECTIVE, lathe);
            post("/api/work-orders/%d/cancel".formatted(cancelled), """
                    {"reason": "Doublon"}
                    """);

            assertThat(mvc.get().uri("/api/work-orders/board"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$[*].id").asArray()
                    .containsExactly((int) urgent, (int) low);
        }

        @Test
        void boardIncludesRecentlyCompletedWork() {
            long id = createWorkOrder(WorkOrderType.CORRECTIVE, press);
            plan(id);
            post("/api/work-orders/%d/start".formatted(id), "");
            complete(id);

            assertThat(mvc.get().uri("/api/work-orders/board"))
                    .bodyJson().extractingPath("$[0].status").isEqualTo("DONE");
        }

        @Test
        void searchFiltersByStatusTypeAndEquipment() {
            Equipment lathe = fixtures.equipment("TOUR-01", Criticality.LOW);
            long pressBreakdown = createWorkOrder(WorkOrderType.CORRECTIVE, press);
            createWorkOrder(WorkOrderType.PREVENTIVE, press);
            long latheBreakdown = createWorkOrder(WorkOrderType.CORRECTIVE, lathe);
            plan(latheBreakdown);

            assertThat(mvc.get().uri("/api/work-orders?type=CORRECTIVE&status=OPEN&status=PLANNED&sort=id"))
                    .bodyJson().extractingPath("$.content[*].id").asArray()
                    .containsExactly((int) pressBreakdown, (int) latheBreakdown);
            assertThat(mvc.get().uri("/api/work-orders?equipmentId={id}&type=PREVENTIVE", press.getId()))
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(1);
            assertThat(mvc.get().uri("/api/work-orders?assigneeId={id}", technician.getId()))
                    .bodyJson().extractingPath("$.content[*].id").asArray().containsExactly((int) latheBreakdown);
            assertThat(mvc.get().uri("/api/work-orders?q=tour-01&priority=LOW"))
                    .bodyJson().extractingPath("$.content[*].id").asArray().containsExactly((int) latheBreakdown);
        }

        @Test
        void listingWorkOrdersDoesNotTriggerNPlusOneQueries() {
            for (int i = 0; i < 10; i++) {
                Equipment equipment = fixtures.equipment("EQ-%03d".formatted(i), Criticality.MEDIUM);
                long id = createWorkOrder(WorkOrderType.CORRECTIVE, equipment);
                plan(id);
            }
            sql.reset();

            assertThat(mvc.get().uri("/api/work-orders?size=50"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.content").asArray().hasSize(10);

            // one SELECT with joins for the page; no COUNT needed as the page is not full
            assertThat(sql.preparedStatements()).isEqualTo(1);
        }
    }
}
