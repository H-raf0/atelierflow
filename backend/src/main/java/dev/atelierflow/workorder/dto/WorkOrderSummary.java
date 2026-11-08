package dev.atelierflow.workorder.dto;

import dev.atelierflow.user.dto.UserSummary;
import dev.atelierflow.workorder.Priority;
import dev.atelierflow.workorder.WorkOrderStatus;
import dev.atelierflow.workorder.WorkOrderType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * Work order card, as shown in lists and on the kanban board.
 *
 * @param allowedTransitions statuses this work order can move to, so the UI only offers legal moves
 */
public record WorkOrderSummary(
        Long id,
        String reference,
        WorkOrderType type,
        WorkOrderStatus status,
        Priority priority,
        String title,
        Long equipmentId,
        String equipmentCode,
        String equipmentName,
        @Schema(nullable = true) UserSummary assignee,
        Instant reportedAt,
        @Schema(nullable = true) LocalDate scheduledFor,
        Set<WorkOrderStatus> allowedTransitions) {
}
