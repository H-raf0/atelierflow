package dev.atelierflow.maintenance;

import dev.atelierflow.maintenance.dto.MaintenancePlanResponse;
import java.time.LocalDate;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface MaintenancePlanMapper {

    @Mapping(target = "equipmentId", source = "equipment.id")
    @Mapping(target = "equipmentCode", source = "equipment.code")
    @Mapping(target = "equipmentName", source = "equipment.name")
    @Mapping(target = "nextDueDate", expression = "java(plan.nextDueDate())")
    @Mapping(target = "remainingHours", expression = "java(plan.remainingHours())")
    @Mapping(target = "due", expression = "java(plan.isDue(today))")
    MaintenancePlanResponse toResponse(MaintenancePlan plan, @Context LocalDate today);
}
