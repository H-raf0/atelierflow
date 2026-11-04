package dev.atelierflow.equipment.dto;

import dev.atelierflow.equipment.Criticality;
import dev.atelierflow.equipment.EquipmentStatus;

/**
 * Optional search criteria, bound from the query string ({@code ?q=press&workshop=Usinage&status=DOWN}).
 *
 * @param q free text matched against code and name
 */
public record EquipmentFilter(String q, String workshop, Criticality criticality, EquipmentStatus status) {
}
