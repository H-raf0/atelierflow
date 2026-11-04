package dev.atelierflow.equipment;

/**
 * How badly a failure of the equipment impacts production. Drives the default priority of corrective
 * work orders: a breakdown on a {@link #CRITICAL} machine stops the line.
 */
public enum Criticality {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
