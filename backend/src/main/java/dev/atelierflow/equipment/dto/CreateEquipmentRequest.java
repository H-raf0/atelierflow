package dev.atelierflow.equipment.dto;

import dev.atelierflow.equipment.Criticality;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateEquipmentRequest(
        @NotBlank
        @Pattern(regexp = "[A-Z0-9][A-Z0-9-]{2,19}",
                message = "must be 3 to 20 uppercase letters, digits or dashes")
        String code,

        @NotBlank @Size(max = 120)
        String name,

        @NotBlank @Size(max = 60)
        String workshop,

        @NotNull
        Criticality criticality,

        /** Optional: hours already run when the machine is registered (0 for a new machine). */
        @PositiveOrZero
        Double operatingHours,

        @PastOrPresent
        LocalDate commissionedOn) {

    public CreateEquipmentRequest {
        if (operatingHours == null) {
            operatingHours = 0.0;
        }
    }
}
