package dev.atelierflow.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.atelierflow.equipment.Criticality;
import dev.atelierflow.equipment.Equipment;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MaintenancePlanTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    private final Equipment press = new Equipment("PRS-001", "Presse", "Emboutissage", Criticality.HIGH, 1000, null);

    @Test
    void calendarPlanIsDueOnceTheIntervalHasElapsed() {
        var plan = MaintenancePlan.everyDays(press, "Inspection", null, 30, TODAY.minusDays(29));

        assertThat(plan.nextDueDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(plan.remainingHours()).isNull();
        assertThat(plan.isDue(TODAY)).isFalse();
        assertThat(plan.isDue(TODAY.plusDays(1))).isTrue();
        assertThat(plan.isDue(TODAY.plusDays(10))).isTrue();
    }

    @Test
    void hourPlanIsDueOnceTheCounterHasRunTheInterval() {
        var plan = MaintenancePlan.everyOperatingHours(press, "Vidange", null, 500, TODAY, 600);

        assertThat(plan.remainingHours()).isEqualTo(100.0);
        assertThat(plan.nextDueDate()).isNull();
        assertThat(plan.isDue(TODAY)).isFalse();

        press.addOperatingHours(100);
        assertThat(plan.remainingHours()).isZero();
        assertThat(plan.isDue(TODAY)).isTrue();
    }

    @Test
    void generatingAWorkOrderMovesTheBaselineForward() {
        var plan = MaintenancePlan.everyOperatingHours(press, "Vidange", null, 500, TODAY.minusYears(1), 0);
        assertThat(plan.isDue(TODAY)).isTrue();

        plan.markServiceGenerated(TODAY);

        assertThat(plan.getLastServiceDate()).isEqualTo(TODAY);
        assertThat(plan.getLastServiceHours()).isEqualTo(1000);
        assertThat(plan.isDue(TODAY)).isFalse();
    }

    @Test
    void inactivePlanIsNeverDue() {
        var plan = MaintenancePlan.everyDays(press, "Inspection", null, 30, TODAY.minusYears(1));

        plan.update("Inspection", "Checklist v2", 60, false);

        assertThat(plan.isDue(TODAY)).isFalse();
        assertThat(plan.getIntervalDays()).isEqualTo(60);
        assertThat(plan.getInstructions()).isEqualTo("Checklist v2");
    }

    @Test
    void updateChangesTheIntervalMatchingTheTrigger() {
        var plan = MaintenancePlan.everyOperatingHours(press, "Vidange", null, 500, TODAY, 1000);

        plan.update("Vidange", null, 250, true);

        assertThat(plan.getIntervalHours()).isEqualTo(250);
        assertThat(plan.getIntervalDays()).isNull();
    }

    @Test
    void intervalMustBePositive() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MaintenancePlan.everyDays(press, "X", null, 0, TODAY));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MaintenancePlan.everyOperatingHours(press, "X", null, -1, TODAY, 0));
    }
}
