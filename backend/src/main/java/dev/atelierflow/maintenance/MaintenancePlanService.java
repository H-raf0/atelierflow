package dev.atelierflow.maintenance;

import dev.atelierflow.common.error.ResourceNotFoundException;
import dev.atelierflow.equipment.Equipment;
import dev.atelierflow.equipment.EquipmentRepository;
import dev.atelierflow.maintenance.dto.CreateMaintenancePlanRequest;
import dev.atelierflow.maintenance.dto.MaintenancePlanResponse;
import dev.atelierflow.maintenance.dto.UpdateMaintenancePlanRequest;
import dev.atelierflow.workorder.WorkOrderService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MaintenancePlanService {

    private static final Logger LOG = LoggerFactory.getLogger(MaintenancePlanService.class);

    private final MaintenancePlanRepository plans;
    private final EquipmentRepository equipments;
    private final WorkOrderService workOrders;
    private final MaintenancePlanMapper mapper;
    private final Clock clock;

    public MaintenancePlanService(MaintenancePlanRepository plans, EquipmentRepository equipments,
                                  WorkOrderService workOrders, MaintenancePlanMapper mapper, Clock clock) {
        this.plans = plans;
        this.equipments = equipments;
        this.workOrders = workOrders;
        this.mapper = mapper;
        this.clock = clock;
    }

    public List<MaintenancePlanResponse> list(Long equipmentId) {
        List<MaintenancePlan> found = equipmentId == null
                ? plans.findAllByOrderByIdAsc()
                : plans.findByEquipmentIdOrderByIdAsc(equipmentId);
        LocalDate today = today();
        return found.stream().map(plan -> mapper.toResponse(plan, today)).toList();
    }

    public MaintenancePlanResponse get(Long id) {
        return mapper.toResponse(findOrThrow(id), today());
    }

    @Transactional
    public MaintenancePlanResponse create(CreateMaintenancePlanRequest request) {
        Equipment equipment = equipments.findById(request.equipmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Equipment", request.equipmentId()));
        LocalDate lastService = request.lastServiceDate() != null ? request.lastServiceDate() : today();
        MaintenancePlan plan = switch (request.trigger()) {
            case CALENDAR -> MaintenancePlan.everyDays(
                    equipment, request.title(), request.instructions(), request.interval(), lastService);
            case OPERATING_HOURS -> MaintenancePlan.everyOperatingHours(
                    equipment, request.title(), request.instructions(), request.interval(), lastService,
                    request.lastServiceHours() != null ? request.lastServiceHours() : equipment.getOperatingHours());
        };
        return mapper.toResponse(plans.save(plan), today());
    }

    @Transactional
    public MaintenancePlanResponse update(Long id, UpdateMaintenancePlanRequest request) {
        MaintenancePlan plan = findOrThrow(id);
        plan.update(request.title(), request.instructions(), request.interval(), request.active());
        return mapper.toResponse(plan, today());
    }

    @Transactional
    public void delete(Long id) {
        plans.delete(findOrThrow(id));
    }

    /**
     * Generates a preventive work order for every due plan, unless the plan already has one waiting
     * (a late intervention must not pile up duplicates). Runs in a single transaction: either all
     * due plans are processed, or none.
     *
     * @return the number of work orders created
     */
    @Transactional
    public int generateDueWorkOrders() {
        LocalDate today = today();
        int created = 0;
        for (MaintenancePlan plan : plans.findByActiveTrue()) {
            if (plan.isDue(today) && !workOrders.hasActiveWorkOrderForPlan(plan.getId())) {
                workOrders.createPreventive(plan.getEquipment().getId(), plan.getId(),
                        "Préventif — " + plan.getTitle(), plan.getInstructions());
                plan.markServiceGenerated(today);
                created++;
            }
        }
        LOG.info("Preventive maintenance: {} work order(s) generated", created);
        return created;
    }

    private MaintenancePlan findOrThrow(Long id) {
        return plans.findWithEquipmentById(id).orElseThrow(() -> new ResourceNotFoundException("Maintenance plan", id));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
