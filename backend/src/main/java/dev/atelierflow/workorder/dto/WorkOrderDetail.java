package dev.atelierflow.workorder.dto;

import dev.atelierflow.equipment.Criticality;
import dev.atelierflow.user.dto.UserSummary;
import dev.atelierflow.workorder.Priority;
import dev.atelierflow.workorder.WorkOrderStatus;
import dev.atelierflow.workorder.WorkOrderType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/** Full work order, as shown on its detail page. */
public record WorkOrderDetail(
        Long id,
        String reference,
        WorkOrderType type,
        WorkOrderStatus status,
        Priority priority,
        String title,
        String description,
        Long equipmentId,
        String equipmentCode,
        String equipmentName,
        String equipmentWorkshop,
        Criticality equipmentCriticality,
        UserSummary assignee,
        Instant reportedAt,
        LocalDate scheduledFor,
        Instant startedAt,
        Instant completedAt,
        Integer timeSpentMinutes,
        String resolution,
        Instant cancelledAt,
        String cancellationReason,
        Long maintenancePlanId,
        Set<WorkOrderStatus> allowedTransitions,
        Instant createdAt,
        Instant updatedAt) {
}
