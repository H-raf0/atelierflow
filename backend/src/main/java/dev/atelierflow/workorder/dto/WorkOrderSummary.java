package dev.atelierflow.workorder.dto;

import dev.atelierflow.user.dto.UserSummary;
import dev.atelierflow.workorder.Priority;
import dev.atelierflow.workorder.WorkOrderStatus;
import dev.atelierflow.workorder.WorkOrderType;
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
        UserSummary assignee,
        Instant reportedAt,
        LocalDate scheduledFor,
        Set<WorkOrderStatus> allowedTransitions) {
}
