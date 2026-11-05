package dev.atelierflow.workorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.atelierflow.equipment.Criticality;
import dev.atelierflow.equipment.Equipment;
import dev.atelierflow.user.AppUser;
import dev.atelierflow.user.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Exhaustive test of the work order life cycle: for each of the 5 × 5 (from, to) pairs, the business
 * method leading to {@code to} is invoked on a work order in state {@code from}. Legal moves must
 * succeed, every other one must throw {@link InvalidTransitionException} and leave the work order as it was.
 *
 * <p>The expected table is written out independently here, so a wrong edit of
 * {@link WorkOrderStatus}'s transition map is caught.
 */
class WorkOrderStateMachineTest {

    private static final Set<String> LEGAL = Set.of(
            "OPEN->PLANNED",
            "OPEN->CANCELLED",
            "PLANNED->OPEN",
            "PLANNED->IN_PROGRESS",
            "PLANNED->CANCELLED",
            "IN_PROGRESS->DONE");

    private static final Instant REPORTED = Instant.parse("2026-11-02T08:00:00Z");
    private static final Instant LATER = REPORTED.plusSeconds(3600);
    private static final AppUser TECHNICIAN = new AppUser("tech", "Tech Nicien", Role.TECHNICIAN);

    static Stream<Arguments> allPairs() {
        return Arrays.stream(WorkOrderStatus.values())
                .flatMap(from -> Arrays.stream(WorkOrderStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairs")
    void transitionTableMatchesSpecification(WorkOrderStatus from, WorkOrderStatus to) {
        assertThat(from.canTransitionTo(to)).isEqualTo(LEGAL.contains(from + "->" + to));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairs")
    void businessMethodsEnforceTheTransitionTable(WorkOrderStatus from, WorkOrderStatus to) {
        WorkOrder workOrder = workOrderIn(from);

        if (LEGAL.contains(from + "->" + to)) {
            moveTo(workOrder, to);
            assertThat(workOrder.getStatus()).isEqualTo(to);
        } else {
            assertThatThrownBy(() -> moveTo(workOrder, to))
                    .isInstanceOf(InvalidTransitionException.class)
                    .hasMessage("A work order cannot go from %s to %s".formatted(from, to))
                    .extracting("code").isEqualTo("INVALID_TRANSITION");
            assertThat(workOrder.getStatus()).isEqualTo(from);
        }
    }

    @ParameterizedTest
    @MethodSource("terminalStatuses")
    void terminalStatusesAllowNoTransition(WorkOrderStatus status) {
        assertThat(status.isTerminal()).isTrue();
        assertThat(status.allowedTransitions()).isEmpty();
    }

    static Stream<WorkOrderStatus> terminalStatuses() {
        return Stream.of(WorkOrderStatus.DONE, WorkOrderStatus.CANCELLED);
    }

    /** Builds a work order in the requested state by walking the legal path. */
    static WorkOrder workOrderIn(WorkOrderStatus status) {
        var equipment = new Equipment("PRS-001", "Presse", "Emboutissage", Criticality.HIGH, 0, null);
        var workOrder = new WorkOrder(equipment, WorkOrderType.CORRECTIVE, Priority.HIGH, "Fuite", null, REPORTED);
        switch (status) {
            case OPEN -> { }
            case PLANNED -> workOrder.plan(TECHNICIAN, LocalDate.of(2026, 11, 3));
            case IN_PROGRESS -> {
                workOrder.plan(TECHNICIAN, LocalDate.of(2026, 11, 3));
                workOrder.start(LATER);
            }
            case DONE -> {
                workOrder.plan(TECHNICIAN, LocalDate.of(2026, 11, 3));
                workOrder.start(LATER);
                workOrder.complete(45, "Joint remplacé", LATER.plusSeconds(2700));
            }
            case CANCELLED -> workOrder.cancel("Doublon", LATER);
        }
        return workOrder;
    }

    /** Calls the business method whose target is {@code target}, with valid arguments. */
    private static void moveTo(WorkOrder workOrder, WorkOrderStatus target) {
        Instant now = LATER.plusSeconds(7200);
        switch (target) {
            case OPEN -> workOrder.unplan();
            case PLANNED -> workOrder.plan(TECHNICIAN, LocalDate.of(2026, 11, 4));
            case IN_PROGRESS -> workOrder.start(now);
            case DONE -> workOrder.complete(30, "Réparé", now);
            case CANCELLED -> workOrder.cancel("Plus nécessaire", now);
        }
    }
}
