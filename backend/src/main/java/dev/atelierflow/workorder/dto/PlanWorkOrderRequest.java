package dev.atelierflow.workorder.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PlanWorkOrderRequest(@NotNull Long assigneeId, @NotNull LocalDate scheduledFor) {
}
