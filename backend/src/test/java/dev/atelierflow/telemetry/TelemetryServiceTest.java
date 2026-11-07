package dev.atelierflow.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.atelierflow.equipment.Criticality;
import dev.atelierflow.equipment.Equipment;
import dev.atelierflow.equipment.EquipmentRepository;
import dev.atelierflow.equipment.EquipmentStatus;
import dev.atelierflow.workorder.WorkOrderService;
import java.time.Duration;
import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TelemetryServiceTest {

    private static final TelemetryProperties PROPERTIES = new TelemetryProperties(true, Duration.ofSeconds(30), 0.5, 0.1);

    @Mock
    private EquipmentRepository equipments;

    @Mock
    private WorkOrderService workOrders;

    /** Deterministic generator: always returns the given value for nextDouble and 0 for nextInt. */
    private static RandomGenerator fixed(double value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public double nextDouble() {
                return value;
            }

            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }

    private static Equipment machine(long id, String code, Criticality criticality) {
        var equipment = new Equipment(code, "Machine", "Usinage", criticality, 10, null);
        ReflectionTestUtils.setField(equipment, "id", id);
        return equipment;
    }

    @Test
    void runningMachinesAccumulateHoursWithoutFailureWhenLucky() {
        var lathe = machine(1, "TOUR-01", Criticality.LOW);
        when(equipments.findByStatusOrderById(EquipmentStatus.OPERATIONAL)).thenReturn(List.of(lathe));

        var result = new TelemetryService(equipments, workOrders, PROPERTIES, fixed(0.99)).tick();

        assertThat(lathe.getOperatingHours()).isEqualTo(10.5);
        assertThat(result.machinesRunning()).isEqualTo(1);
        assertThat(result.brokenDown()).isEmpty();
        verify(workOrders, never()).reportBreakdown(anyLong(), anyString(), anyString());
    }

    @Test
    void failureProbabilityIsWeightedByCriticality() {
        // draw = 0.12: above 0.1 × 0.5 (LOW) and 0.1 × 1.0 (MEDIUM), below 0.1 × 1.5 (HIGH) and 0.1 × 2 (CRITICAL)
        var low = machine(1, "LOW-01", Criticality.LOW);
        var medium = machine(2, "MED-01", Criticality.MEDIUM);
        var high = machine(3, "HIGH-01", Criticality.HIGH);
        var critical = machine(4, "CRIT-01", Criticality.CRITICAL);
        when(equipments.findByStatusOrderById(EquipmentStatus.OPERATIONAL))
                .thenReturn(List.of(low, medium, high, critical));

        var result = new TelemetryService(equipments, workOrders, PROPERTIES, fixed(0.12)).tick();

        assertThat(result.brokenDown()).containsExactly("HIGH-01", "CRIT-01");
        verify(workOrders).reportBreakdown(eq(3L), eq(TelemetryService.FAILURE_MODES.getFirst()),
                contains("télémétrie"));
        verify(workOrders).reportBreakdown(eq(4L), anyString(), anyString());
        verify(workOrders, never()).reportBreakdown(eq(1L), anyString(), anyString());
    }
}
