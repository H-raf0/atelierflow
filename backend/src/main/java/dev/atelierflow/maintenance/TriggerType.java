package dev.atelierflow.maintenance;

/** What makes a preventive intervention due. */
public enum TriggerType {
    /** Every N days (e.g. monthly inspection). */
    CALENDAR,
    /** Every N hours of operation (e.g. oil change every 500 h), driven by the hour counter. */
    OPERATING_HOURS
}
