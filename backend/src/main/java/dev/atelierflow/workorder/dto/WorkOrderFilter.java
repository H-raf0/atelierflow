package dev.atelierflow.workorder.dto;

import dev.atelierflow.workorder.Priority;
import dev.atelierflow.workorder.WorkOrderStatus;
import dev.atelierflow.workorder.WorkOrderType;
import java.util.Set;

/**
 * Optional search criteria bound from the query string; {@code status} accepts several values
 * ({@code ?status=OPEN&status=PLANNED}).
 *
 * @param q free text matched against the title and the equipment code
 */
public record WorkOrderFilter(String q, Set<WorkOrderStatus> status, WorkOrderType type, Priority priority,
                              Long equipmentId, Long assigneeId) {
}
