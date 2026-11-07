package dev.atelierflow.maintenance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** The trigger type and the machine of a plan cannot change; create another plan instead. */
public record UpdateMaintenancePlanRequest(
        @NotBlank @Size(max = 150) String title,
        @Size(max = 4000) String instructions,
        @NotNull @Positive @Max(100_000) Integer interval,
        @NotNull Boolean active) {
}
