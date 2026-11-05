package dev.atelierflow.workorder;

import dev.atelierflow.common.error.ResourceNotFoundException;
import dev.atelierflow.common.web.PageResponse;
import dev.atelierflow.equipment.Equipment;
import dev.atelierflow.equipment.EquipmentRepository;
import dev.atelierflow.equipment.EquipmentStatus;
import dev.atelierflow.user.AppUser;
import dev.atelierflow.user.UserRepository;
import dev.atelierflow.workorder.dto.CancelWorkOrderRequest;
import dev.atelierflow.workorder.dto.CompleteWorkOrderRequest;
import dev.atelierflow.workorder.dto.CreateWorkOrderRequest;
import dev.atelierflow.workorder.dto.PlanWorkOrderRequest;
import dev.atelierflow.workorder.dto.UpdateWorkOrderRequest;
import dev.atelierflow.workorder.dto.WorkOrderDetail;
import dev.atelierflow.workorder.dto.WorkOrderFilter;
import dev.atelierflow.workorder.dto.WorkOrderSummary;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Work order use cases. The service loads the aggregate, delegates the rule to the entity
 * (which owns the state machine) and keeps the machine status consistent with its work orders.
 */
@Service
@Transactional(readOnly = true)
public class WorkOrderService {

    /** Completed work orders stay visible in the DONE column of the board for one week. */
    static final Duration BOARD_DONE_WINDOW = Duration.ofDays(7);

    private static final Comparator<WorkOrder> BOARD_ORDER = Comparator
            .comparing(WorkOrder::getPriority, Comparator.reverseOrder())
            .thenComparing(WorkOrder::getReportedAt);

    private final WorkOrderRepository workOrders;
    private final EquipmentRepository equipments;
    private final UserRepository users;
    private final WorkOrderMapper mapper;
    private final Clock clock;

    public WorkOrderService(WorkOrderRepository workOrders, EquipmentRepository equipments, UserRepository users,
                            WorkOrderMapper mapper, Clock clock) {
        this.workOrders = workOrders;
        this.equipments = equipments;
        this.users = users;
        this.mapper = mapper;
        this.clock = clock;
    }

    public PageResponse<WorkOrderSummary> search(WorkOrderFilter filter, Pageable pageable) {
        return PageResponse.from(
                workOrders.findAll(WorkOrderSpecifications.matching(filter), pageable).map(mapper::toSummary));
    }

    /** Cards of the kanban board, most urgent first within each column. */
    public List<WorkOrderSummary> board() {
        Instant doneSince = clock.instant().minus(BOARD_DONE_WINDOW);
        return workOrders.findBoard(WorkOrderStatus.ACTIVE, doneSince).stream()
                .sorted(BOARD_ORDER)
                .map(mapper::toSummary)
                .toList();
    }

    public WorkOrderDetail get(Long id) {
        return mapper.toDetail(findOrThrow(id));
    }

    @Transactional
    public WorkOrderDetail create(CreateWorkOrderRequest request) {
        Equipment equipment = equipments.findById(request.equipmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Equipment", request.equipmentId()));
        Priority priority = request.priority() != null
                ? request.priority()
                : Priority.forBreakdownOf(equipment.getCriticality());
        WorkOrder workOrder = new WorkOrder(equipment, request.type(), priority, request.title(),
                request.description(), clock.instant());
        return saveAndRefresh(workOrder);
    }

    @Transactional
    public WorkOrderDetail update(Long id, UpdateWorkOrderRequest request) {
        return apply(id, wo -> wo.updateDetails(request.title(), request.description(), request.priority()));
    }

    @Transactional
    public WorkOrderDetail plan(Long id, PlanWorkOrderRequest request) {
        AppUser technician = users.findById(request.assigneeId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.assigneeId()));
        return apply(id, wo -> wo.plan(technician, request.scheduledFor()));
    }

    @Transactional
    public WorkOrderDetail unplan(Long id) {
        return apply(id, WorkOrder::unplan);
    }

    @Transactional
    public WorkOrderDetail start(Long id) {
        return apply(id, wo -> wo.start(clock.instant()));
    }

    @Transactional
    public WorkOrderDetail complete(Long id, CompleteWorkOrderRequest request) {
        return apply(id, wo -> wo.complete(request.timeSpentMinutes(), request.resolution(), clock.instant()));
    }

    @Transactional
    public WorkOrderDetail cancel(Long id, CancelWorkOrderRequest request) {
        return apply(id, wo -> wo.cancel(request.reason(), clock.instant()));
    }

    private WorkOrderDetail apply(Long id, Consumer<WorkOrder> change) {
        WorkOrder workOrder = findOrThrow(id);
        change.accept(workOrder);
        return saveAndRefresh(workOrder);
    }

    private WorkOrderDetail saveAndRefresh(WorkOrder workOrder) {
        workOrders.saveAndFlush(workOrder);
        refreshEquipmentStatus(workOrder.getEquipment());
        return mapper.toDetail(workOrder);
    }

    /**
     * Derives the machine status from its work orders: down while a breakdown is not repaired,
     * in maintenance while a preventive intervention is in progress, operational otherwise.
     */
    private void refreshEquipmentStatus(Equipment equipment) {
        EquipmentStatus status;
        if (workOrders.existsByEquipmentIdAndTypeAndStatusIn(
                equipment.getId(), WorkOrderType.CORRECTIVE, WorkOrderStatus.ACTIVE)) {
            status = EquipmentStatus.DOWN;
        } else if (workOrders.existsByEquipmentIdAndTypeAndStatusIn(
                equipment.getId(), WorkOrderType.PREVENTIVE, EnumSet.of(WorkOrderStatus.IN_PROGRESS))) {
            status = EquipmentStatus.IN_MAINTENANCE;
        } else {
            status = EquipmentStatus.OPERATIONAL;
        }
        equipment.changeStatus(status);
    }

    private WorkOrder findOrThrow(Long id) {
        return workOrders.findWithDetailsById(id).orElseThrow(() -> new ResourceNotFoundException("Work order", id));
    }
}
