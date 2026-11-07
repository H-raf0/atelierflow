package dev.atelierflow.maintenance.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.atelierflow.maintenance.TriggerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * @param interval         days for a CALENDAR plan, operating hours for an OPERATING_HOURS plan
 * @param lastServiceDate  optional: date of the last intervention (defaults to today)
 * @param lastServiceHours optional, hour-based plans: counter at the last intervention (defaults to the
 *                         current counter)
 */
public record CreateMaintenancePlanRequest(
        @NotNull Long equipmentId,
        @NotBlank @Size(max = 150) String title,
        @Size(max = 4000) String instructions,
        @NotNull TriggerType trigger,
        @NotNull @Positive @Max(100_000) Integer interval,
        @PastOrPresent LocalDate lastServiceDate,
        @PositiveOrZero Double lastServiceHours) {

    /** Cross-field rule; named so that it cannot shadow the {@code lastServiceHours} JSON property. */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "lastServiceHours is only meaningful for an OPERATING_HOURS plan")
    public boolean isLastServiceHoursConsistent() {
        return lastServiceHours == null || trigger == TriggerType.OPERATING_HOURS;
    }
}
