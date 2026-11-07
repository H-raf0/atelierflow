package dev.atelierflow.workorder;

import dev.atelierflow.equipment.Criticality;

public enum Priority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT;

    /** Default priority of a breakdown: the more critical the machine, the more urgent the repair. */
    public static Priority forBreakdownOf(Criticality criticality) {
        return switch (criticality) {
            case LOW -> LOW;
            case MEDIUM -> MEDIUM;
            case HIGH -> HIGH;
            case CRITICAL -> URGENT;
        };
    }

    /** Preventive work can wait a little, except on the machines the line cannot run without. */
    public static Priority forPreventiveOn(Criticality criticality) {
        return criticality == Criticality.CRITICAL ? HIGH : MEDIUM;
    }
}
