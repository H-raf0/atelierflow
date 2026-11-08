package dev.atelierflow.workorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * One committed change of a work order.
 *
 * @param action  CREATED or UPDATED
 * @param changes fields that differ from the previous revision (all set fields for the creation)
 */
public record HistoryEntry(long revision, Instant timestamp, String username, String action, List<FieldChange> changes) {

    /** Old and new value of one field, rendered as text (null when empty). */
    public record FieldChange(String field, @Schema(nullable = true) String from, @Schema(nullable = true) String to) {
    }
}
