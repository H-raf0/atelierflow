package dev.atelierflow.equipment;

/** Published when a machine goes down, into maintenance or back to operation. */
public record EquipmentStatusChangedEvent(Long equipmentId, String code, EquipmentStatus status) {
}
