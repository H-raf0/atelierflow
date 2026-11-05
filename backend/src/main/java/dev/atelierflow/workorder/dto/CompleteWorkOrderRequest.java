package dev.atelierflow.workorder.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * @param timeSpentMinutes labour time; capped at one week to catch typing mistakes
 */
public record CompleteWorkOrderRequest(
        @NotNull @Positive @Max(10_080) Integer timeSpentMinutes,
        @NotBlank @Size(max = 4000) String resolution) {
}
