package dev.atelierflow.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.atelierflow.equipment.Criticality;
import dev.atelierflow.equipment.Equipment;
import dev.atelierflow.equipment.EquipmentRepository;
import dev.atelierflow.support.IntegrationTest;
import dev.atelierflow.user.Role;
import dev.atelierflow.workorder.Priority;
import dev.atelierflow.workorder.WorkOrder;
import dev.atelierflow.workorder.WorkOrderRepository;
import dev.atelierflow.workorder.WorkOrderStatus;
import dev.atelierflow.workorder.WorkOrderType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class MaintenancePlanIntegrationTest extends IntegrationTest {

    @Autowired
    private WorkOrderRepository workOrders;

    @Autowired
    private MaintenancePlanService service;

    @Autowired
    private EquipmentRepository equipments;

    private Equipment press;

    @BeforeEach
    void setUp() {
        press = fixtures.equipment("PRS-001", Criticality.CRITICAL); // 100 operating hours
    }

    private MvcTestResult createPlan(String json) {
        return mvc.post().uri("/api/maintenance-plans").contentType(MediaType.APPLICATION_JSON)
                .content(json.formatted(press.getId())).exchange();
    }

    @Test
    void createsCalendarAndHourPlansWithTheirNextDeadline() {
        MvcTestResult calendar = createPlan("""
                {"equipmentId": %d, "title": "Inspection sécurité", "trigger": "CALENDAR", "interval": 30}
                """);
        assertThat(calendar).hasStatus(HttpStatus.CREATED);
        assertThat(calendar).bodyJson().extractingPath("$.nextDueDate")
                .isEqualTo(LocalDate.now().plusDays(30).toString());
        assertThat(calendar).bodyJson().extractingPath("$.due").isEqualTo(false);

        MvcTestResult hours = createPlan("""
                {"equipmentId": %d, "title": "Vidange", "trigger": "OPERATING_HOURS", "interval": 500,
                 "lastServiceHours": 0}
                """);
        assertThat(hours).hasStatus(HttpStatus.CREATED);
        assertThat(hours).bodyJson().extractingPath("$.remainingHours").isEqualTo(400.0);

        assertThat(mvc.get().uri("/api/maintenance-plans?equipmentId={id}", press.getId()))
                .bodyJson().extractingPath("$[*].title").asArray().containsExactly("Inspection sécurité", "Vidange");
    }

    @Test
    void rejectsInconsistentPlans() {
        MvcTestResult result = createPlan("""
                {"equipmentId": %d, "title": "Inspection", "trigger": "CALENDAR", "interval": 0, "lastServiceHours": 10}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.violations[*].field").asArray()
                .containsExactlyInAnyOrder("interval", "lastServiceHoursConsistent");
    }

    @Test
    void generatesPreventiveWorkOrdersForDuePlansWithoutDuplicates() {
        createPlan("""
                {"equipmentId": %%d, "title": "Graissage", "instructions": "Points 1 à 12",
                 "trigger": "CALENDAR", "interval": 30, "lastServiceDate": "%s"}
                """.formatted(LocalDate.now().minusDays(45)));
        createPlan("""
                {"equipmentId": %d, "title": "Changement filtre", "trigger": "OPERATING_HOURS", "interval": 500}
                """);

        assertThat(mvc.post().uri("/api/maintenance-plans/generate"))
                .hasStatusOk().bodyJson().extractingPath("$.created").isEqualTo(1);

        List<WorkOrder> generated = workOrders.findAll();
        assertThat(generated).singleElement().satisfies(wo -> {
            assertThat(wo.getType()).isEqualTo(WorkOrderType.PREVENTIVE);
            assertThat(wo.getTitle()).isEqualTo("Préventif — Graissage");
            assertThat(wo.getDescription()).isEqualTo("Points 1 à 12");
            assertThat(wo.getPriority()).isEqualTo(Priority.HIGH); // critical machine
            assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.OPEN);
            assertThat(wo.getMaintenancePlanId()).isNotNull();
        });

        // the plan restarts from today: running the job again creates nothing
        assertThat(service.generateDueWorkOrders()).isZero();
        assertThat(mvc.get().uri("/api/work-orders/{id}", generated.getFirst().getId()))
                .bodyJson().extractingPath("$.maintenancePlanId").isEqualTo(generated.getFirst().getMaintenancePlanId().intValue());
    }

    @Test
    void doesNotGenerateWhileAPreviousOccurrenceIsPending() {
        createPlan("""
                {"equipmentId": %d, "title": "Contrôle", "trigger": "OPERATING_HOURS", "interval": 10, "lastServiceHours": 0}
                """);
        assertThat(service.generateDueWorkOrders()).isEqualTo(1);

        // the counter runs past the next interval, but the first work order is still open
        Equipment reloaded = equipments.findById(press.getId()).orElseThrow();
        reloaded.addOperatingHours(50);
        equipments.save(reloaded);
        assertThat(service.generateDueWorkOrders()).isZero();
        assertThat(workOrders.count()).isEqualTo(1);
    }

    @Test
    void updatesAndDeletesAPlanButKeepsGeneratedWorkOrders() {
        long id = Long.parseLong(createPlan("""
                {"equipmentId": %d, "title": "Contrôle", "trigger": "CALENDAR", "interval": 1, "lastServiceDate": "2026-01-01"}
                """).getResponse().getHeader("Location").replaceAll(".*/", ""));

        assertThat(mvc.put().uri("/api/maintenance-plans/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "Contrôle hebdo", "interval": 7, "active": true}
                        """))
                .hasStatusOk().bodyJson().extractingPath("$.intervalDays").isEqualTo(7);
        service.generateDueWorkOrders();

        assertThat(mvc.delete().uri("/api/maintenance-plans/{id}", id)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/maintenance-plans/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(workOrders.findAll()).singleElement()
                .satisfies(wo -> assertThat(wo.getMaintenancePlanId()).isNull());
    }

    @Test
    void techniciansCannotManagePlans() {
        var technician = as(fixtures.user("jdupont", Role.TECHNICIAN));

        assertThat(technician.post().uri("/api/maintenance-plans").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"equipmentId": %d, "title": "X", "trigger": "CALENDAR", "interval": 7}
                        """.formatted(press.getId())))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(technician.post().uri("/api/maintenance-plans/generate")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(technician.get().uri("/api/maintenance-plans")).hasStatusOk();
    }
}
