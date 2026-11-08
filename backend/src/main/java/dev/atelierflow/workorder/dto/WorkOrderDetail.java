package dev.atelierflow.workorder.dto;

import dev.atelierflow.equipment.Criticality;
import dev.atelierflow.user.dto.UserSummary;
import dev.atelierflow.workorder.Priority;
import dev.atelierflow.workorder.WorkOrderStatus;
import dev.atelierflow.workorder.WorkOrderType;
import io.swagger.v3.oas.annotations.media.Schema;
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
        @Schema(nullable = true) String description,
        Long equipmentId,
        String equipmentCode,
        String equipmentName,
        String equipmentWorkshop,
        Criticality equipmentCriticality,
        @Schema(nullable = true) UserSummary assignee,
        Instant reportedAt,
        @Schema(nullable = true) LocalDate scheduledFor,
        @Schema(nullable = true) Instant startedAt,
        @Schema(nullable = true) Instant completedAt,
        @Schema(nullable = true) Integer timeSpentMinutes,
        @Schema(nullable = true) String resolution,
        @Schema(nullable = true) Instant cancelledAt,
        @Schema(nullable = true) String cancellationReason,
        @Schema(nullable = true) Long maintenancePlanId,
        Set<WorkOrderStatus> allowedTransitions,
        Instant createdAt,
        Instant updatedAt) {
}
