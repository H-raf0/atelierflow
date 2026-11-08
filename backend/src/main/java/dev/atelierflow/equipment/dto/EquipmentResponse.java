package dev.atelierflow.equipment.dto;

import dev.atelierflow.equipment.Criticality;
import dev.atelierflow.equipment.EquipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;

public record EquipmentResponse(
        Long id,
        String code,
        String name,
        String workshop,
        Criticality criticality,
        EquipmentStatus status,
        double operatingHours,
        @Schema(nullable = true) LocalDate commissionedOn,
        Instant createdAt,
        Instant updatedAt) {
}
