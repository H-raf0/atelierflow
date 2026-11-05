package dev.atelierflow.workorder;

import static dev.atelierflow.workorder.WorkOrderStateMachineTest.workOrderIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.atelierflow.common.error.BusinessRuleException;
import dev.atelierflow.equipment.Criticality;
import dev.atelierflow.user.AppUser;
import dev.atelierflow.user.Role;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Preconditions and side effects of each transition, beyond the transition table itself. */
class WorkOrderTest {

    private static final AppUser TECHNICIAN = new AppUser("tech", "Tech Nicien", Role.TECHNICIAN);
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");

    @Test
    void planAssignsTechnicianAndDate() {
        WorkOrder workOrder = workOrderIn(WorkOrderStatus.OPEN);

        workOrder.plan(TECHNICIAN, LocalDate.of(2026, 11, 5));

        assertThat(workOrder.getAssignee()).isSameAs(TECHNICIAN);
        assertThat(workOrder.getScheduledFor()).isEqualTo(LocalDate.of(2026, 11, 5));
    }

    @Test
    void onlyATechnicianCanBeAssigned() {
        WorkOrder workOrder = workOrderIn(WorkOrderStatus.OPEN);
        AppUser manager = new AppUser("boss", "Res Ponsable", Role.MANAGER);

        assertThatThrownBy(() -> workOrder.plan(manager, LocalDate.of(2026, 11, 5)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("ASSIGNEE_NOT_TECHNICIAN");
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.OPEN);
        assertThat(workOrder.getAssignee()).isNull();
    }

    @Test
    void unplanWithdrawsTheAssignment() {
        WorkOrder workOrder = workOrderIn(WorkOrderStatus.PLANNED);

        workOrder.unplan();

        assertThat(workOrder.getAssignee()).isNull();
        assertThat(workOrder.getScheduledFor()).isNull();
    }

    @Test
    void startAndCompleteRecordTimestampsTimeAndResolution() {
        WorkOrder workOrder = workOrderIn(WorkOrderStatus.PLANNED);

        workOrder.start(NOW);
        workOrder.complete(90, "Roulement changé", NOW.plusSeconds(5400));

        assertThat(workOrder.getStartedAt()).isEqualTo(NOW);
        assertThat(workOrder.getCompletedAt()).isEqualTo(NOW.plusSeconds(5400));
        assertThat(workOrder.getTimeSpentMinutes()).isEqualTo(90);
        assertThat(workOrder.getResolution()).isEqualTo("Roulement changé");
        assertThat(workOrder.isActive()).isFalse();
    }

    @Test
    void timestampsNeverPrecedeThePreviousStep() {
        WorkOrder workOrder = workOrderIn(WorkOrderStatus.PLANNED);
        Instant beforeReport = workOrder.getReportedAt().minusSeconds(60);

        workOrder.start(beforeReport);
        workOrder.complete(10, "OK", beforeReport);

        assertThat(workOrder.getStartedAt()).isEqualTo(workOrder.getReportedAt());
        assertThat(workOrder.getCompletedAt()).isEqualTo(workOrder.getStartedAt());
    }

    @Test
    void completionRequiresPositiveTimeAndResolution() {
        WorkOrder workOrder = workOrderIn(WorkOrderStatus.IN_PROGRESS);

        assertThatThrownBy(() -> workOrder.complete(0, "OK", NOW))
                .extracting("code").isEqualTo("TIME_SPENT_REQUIRED");
        assertThatThrownBy(() -> workOrder.complete(30, " ", NOW))
                .extracting("code").isEqualTo("RESOLUTION_REQUIRED");
        assertThatThrownBy(() -> workOrder.complete(30, null, NOW))
                .extracting("code").isEqualTo("RESOLUTION_REQUIRED");
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
    }

    @Test
    void cancellationRequiresAReasonAndKeepsIt() {
        WorkOrder workOrder = workOrderIn(WorkOrderStatus.OPEN);

        assertThatThrownBy(() -> workOrder.cancel("", NOW))
                .extracting("code").isEqualTo("CANCELLATION_REASON_REQUIRED");
        assertThatThrownBy(() -> workOrder.cancel(null, NOW))
                .extracting("code").isEqualTo("CANCELLATION_REASON_REQUIRED");

        workOrder.cancel("Doublon de l'OT-00012", NOW);

        assertThat(workOrder.getCancellationReason()).isEqualTo("Doublon de l'OT-00012");
        assertThat(workOrder.getCancelledAt()).isEqualTo(NOW);
    }

    @Test
    void detailsCanBeEditedUntilClosed() {
        WorkOrder open = workOrderIn(WorkOrderStatus.IN_PROGRESS);
        open.updateDetails("Fuite vérin", "Côté gauche", Priority.URGENT);
        assertThat(open.getTitle()).isEqualTo("Fuite vérin");
        assertThat(open.getDescription()).isEqualTo("Côté gauche");
        assertThat(open.getPriority()).isEqualTo(Priority.URGENT);

        WorkOrder done = workOrderIn(WorkOrderStatus.DONE);
        assertThatThrownBy(() -> done.updateDetails("X", null, Priority.LOW))
                .extracting("code").isEqualTo("WORK_ORDER_CLOSED");
    }

    @Test
    void breakdownPriorityFollowsMachineCriticality() {
        assertThat(Priority.forBreakdownOf(Criticality.LOW)).isEqualTo(Priority.LOW);
        assertThat(Priority.forBreakdownOf(Criticality.MEDIUM)).isEqualTo(Priority.MEDIUM);
        assertThat(Priority.forBreakdownOf(Criticality.HIGH)).isEqualTo(Priority.HIGH);
        assertThat(Priority.forBreakdownOf(Criticality.CRITICAL)).isEqualTo(Priority.URGENT);
    }
}
