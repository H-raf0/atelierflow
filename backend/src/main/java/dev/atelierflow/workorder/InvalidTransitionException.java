package dev.atelierflow.workorder;

import dev.atelierflow.common.error.BusinessRuleException;

/** A state change that the work order life cycle does not allow; answered with 409 Conflict. */
public class InvalidTransitionException extends BusinessRuleException {

    public InvalidTransitionException(WorkOrderStatus from, WorkOrderStatus to) {
        super("INVALID_TRANSITION", "A work order cannot go from %s to %s".formatted(from, to));
    }
}
