package dev.atelierflow.equipment;

/** Operational state of a machine, as seen from the shop floor. */
public enum EquipmentStatus {
    /** Running or ready to run. */
    OPERATIONAL,
    /** Broken down: production on this machine is stopped until a corrective intervention ends. */
    DOWN,
    /** Stopped on purpose for a planned (preventive) intervention. */
    IN_MAINTENANCE
}
