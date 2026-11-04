package dev.atelierflow.equipment.dto;

import dev.atelierflow.equipment.Criticality;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Editable attributes of a machine. The code is an immutable business identifier (printed on the
 * machine plate) and the hour counter is owned by the telemetry, so neither can be edited here.
 */
public record UpdateEquipmentRequest(
        @NotBlank @Size(max = 120)
        String name,

        @NotBlank @Size(max = 60)
        String workshop,

        @NotNull
        Criticality criticality,

        @PastOrPresent
        LocalDate commissionedOn) {
}
