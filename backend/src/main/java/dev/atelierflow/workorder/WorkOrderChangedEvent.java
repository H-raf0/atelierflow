package dev.atelierflow.workorder;

import dev.atelierflow.workorder.dto.WorkOrderSummary;

/**
 * Published after any creation or state change of a work order. Listeners (the WebSocket broadcaster)
 * react after the transaction commits, so clients never see a change that is later rolled back.
 */
public record WorkOrderChangedEvent(WorkOrderSummary workOrder) {
}
