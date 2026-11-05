package dev.atelierflow.workorder;

import dev.atelierflow.user.UserMapper;
import dev.atelierflow.workorder.dto.WorkOrderDetail;
import dev.atelierflow.workorder.dto.WorkOrderSummary;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(uses = UserMapper.class)
public interface WorkOrderMapper {

    @Mapping(target = "reference", source = "id", qualifiedByName = "reference")
    @Mapping(target = "equipmentId", source = "equipment.id")
    @Mapping(target = "equipmentCode", source = "equipment.code")
    @Mapping(target = "equipmentName", source = "equipment.name")
    @Mapping(target = "allowedTransitions", source = "status", qualifiedByName = "allowedTransitions")
    WorkOrderSummary toSummary(WorkOrder workOrder);

    @Mapping(target = "reference", source = "id", qualifiedByName = "reference")
    @Mapping(target = "equipmentId", source = "equipment.id")
    @Mapping(target = "equipmentCode", source = "equipment.code")
    @Mapping(target = "equipmentName", source = "equipment.name")
    @Mapping(target = "equipmentWorkshop", source = "equipment.workshop")
    @Mapping(target = "equipmentCriticality", source = "equipment.criticality")
    @Mapping(target = "allowedTransitions", source = "status", qualifiedByName = "allowedTransitions")
    WorkOrderDetail toDetail(WorkOrder workOrder);

    /** Human-friendly number printed on the job sheet, e.g. {@code OT-00042}. */
    @Named("reference")
    default String reference(Long id) {
        return id == null ? null : "OT-%05d".formatted(id);
    }

    @Named("allowedTransitions")
    default Set<WorkOrderStatus> allowedTransitions(WorkOrderStatus status) {
        return status.allowedTransitions();
    }
}
