package dev.atelierflow.telemetry;

import dev.atelierflow.equipment.Criticality;
import dev.atelierflow.equipment.Equipment;
import dev.atelierflow.equipment.EquipmentRepository;
import dev.atelierflow.equipment.EquipmentStatus;
import dev.atelierflow.workorder.WorkOrderService;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simulates the machines' PLCs: running machines accumulate operating hours (which feeds the
 * hour-based maintenance plans) and occasionally break down, which opens a corrective work order.
 */
@Service
public class TelemetryService {

    private static final Logger LOG = LoggerFactory.getLogger(TelemetryService.class);

    /** Realistic failure modes of workshop machines, used as work order titles. */
    static final List<String> FAILURE_MODES = List.of(
            "Surchauffe moteur",
            "Fuite hydraulique",
            "Vibrations anormales",
            "Défaut capteur de position",
            "Arrêt d'urgence déclenché",
            "Rupture de courroie",
            "Défaut variateur",
            "Pression d'air insuffisante");

    private final EquipmentRepository equipments;
    private final WorkOrderService workOrders;
    private final TelemetryProperties properties;
    private final RandomGenerator random;

    public TelemetryService(EquipmentRepository equipments, WorkOrderService workOrders,
                            TelemetryProperties properties, RandomGenerator random) {
        this.equipments = equipments;
        this.workOrders = workOrders;
        this.properties = properties;
        this.random = random;
    }

    /** Outcome of one simulated reading. */
    public record TickResult(int machinesRunning, List<String> brokenDown) {
    }

    @Transactional
    public TickResult tick() {
        List<Equipment> running = equipments.findByStatusOrderById(EquipmentStatus.OPERATIONAL);
        List<Equipment> failures = new ArrayList<>();
        for (Equipment equipment : running) {
            equipment.addOperatingHours(properties.hoursPerTick());
            if (random.nextDouble() < properties.failureProbability() * weight(equipment.getCriticality())) {
                failures.add(equipment);
            }
        }
        for (Equipment equipment : failures) {
            String failureMode = FAILURE_MODES.get(random.nextInt(FAILURE_MODES.size()));
            workOrders.reportBreakdown(equipment.getId(), failureMode,
                    "Panne détectée automatiquement par la télémétrie à %.1f h de fonctionnement."
                            .formatted(equipment.getOperatingHours()));
            LOG.info("Telemetry: breakdown on {} ({})", equipment.getCode(), failureMode);
        }
        return new TickResult(running.size(), failures.stream().map(Equipment::getCode).toList());
    }

    private static double weight(Criticality criticality) {
        return switch (criticality) {
            case LOW -> 0.5;
            case MEDIUM -> 1.0;
            case HIGH -> 1.5;
            case CRITICAL -> 2.0;
        };
    }
}
