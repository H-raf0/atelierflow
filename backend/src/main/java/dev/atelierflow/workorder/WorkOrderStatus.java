package dev.atelierflow.workorder;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Life cycle of a work order, as an explicit finite state machine.
 *
 * <pre>
 *            plan              start               complete
 *   OPEN ───────────▶ PLANNED ───────▶ IN_PROGRESS ─────────▶ DONE
 *    │  ◀───────────    │
 *    │     unplan       │
 *    │ cancel           │ cancel
 *    ▼                  ▼
 *  CANCELLED ◀──────────┘
 * </pre>
 *
 * <p>The transition table is the single source of truth: every state change goes through
 * {@link #canTransitionTo(WorkOrderStatus)}, and the allowed targets are also exposed by the API so
 * the kanban board only offers legal moves. Work that has started cannot be cancelled, it must be
 * completed (the intervention and its duration must be recorded). {@link #DONE} and
 * {@link #CANCELLED} are terminal.
 */
public enum WorkOrderStatus {
    OPEN,
    PLANNED,
    IN_PROGRESS,
    DONE,
    CANCELLED;

    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> TRANSITIONS = new EnumMap<>(WorkOrderStatus.class);

    static {
        TRANSITIONS.put(OPEN, EnumSet.of(PLANNED, CANCELLED));
        TRANSITIONS.put(PLANNED, EnumSet.of(OPEN, IN_PROGRESS, CANCELLED));
        TRANSITIONS.put(IN_PROGRESS, EnumSet.of(DONE));
        TRANSITIONS.put(DONE, EnumSet.noneOf(WorkOrderStatus.class));
        TRANSITIONS.put(CANCELLED, EnumSet.noneOf(WorkOrderStatus.class));
    }

    /** Statuses of work that still has to be done (shown on the kanban board, counted in the backlog). */
    public static final Set<WorkOrderStatus> ACTIVE = Collections.unmodifiableSet(EnumSet.of(OPEN, PLANNED, IN_PROGRESS));

    public boolean canTransitionTo(WorkOrderStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public Set<WorkOrderStatus> allowedTransitions() {
        return Collections.unmodifiableSet(TRANSITIONS.get(this));
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}
