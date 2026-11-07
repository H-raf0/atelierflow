package dev.atelierflow.maintenance;

import dev.atelierflow.common.persistence.BaseEntity;
import dev.atelierflow.equipment.Equipment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Recurring preventive maintenance of one machine, triggered by the calendar or by operating hours.
 *
 * <p>The plan remembers the last service (date and hour counter). It is due once the interval has
 * elapsed since then; generating a work order moves this baseline forward.
 */
@Entity
@Table(name = "maintenance_plan")
public class MaintenancePlan extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false, updatable = false)
    private Equipment equipment;

    @Column(nullable = false, length = 150)
    private String title;

    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 16)
    private TriggerType trigger;

    private Integer intervalDays;
    private Integer intervalHours;

    @Column(nullable = false)
    private LocalDate lastServiceDate;

    @Column(nullable = false)
    private double lastServiceHours;

    @Column(nullable = false)
    private boolean active = true;

    /** Required by JPA. */
    protected MaintenancePlan() {
    }

    private MaintenancePlan(Equipment equipment, String title, String instructions, TriggerType trigger,
                            Integer intervalDays, Integer intervalHours, LocalDate lastServiceDate,
                            double lastServiceHours) {
        this.equipment = Objects.requireNonNull(equipment);
        this.title = Objects.requireNonNull(title);
        this.instructions = instructions;
        this.trigger = Objects.requireNonNull(trigger);
        this.intervalDays = intervalDays;
        this.intervalHours = intervalHours;
        this.lastServiceDate = Objects.requireNonNull(lastServiceDate);
        this.lastServiceHours = lastServiceHours;
    }

    public static MaintenancePlan everyDays(Equipment equipment, String title, String instructions, int days,
                                            LocalDate lastServiceDate) {
        requirePositive(days);
        return new MaintenancePlan(equipment, title, instructions, TriggerType.CALENDAR, days, null,
                lastServiceDate, equipment.getOperatingHours());
    }

    public static MaintenancePlan everyOperatingHours(Equipment equipment, String title, String instructions,
                                                      int hours, LocalDate lastServiceDate, double lastServiceHours) {
        requirePositive(hours);
        return new MaintenancePlan(equipment, title, instructions, TriggerType.OPERATING_HOURS, null, hours,
                lastServiceDate, lastServiceHours);
    }

    public boolean isDue(LocalDate today) {
        if (!active) {
            return false;
        }
        return switch (trigger) {
            case CALENDAR -> !today.isBefore(nextDueDate());
            case OPERATING_HOURS -> remainingHours() <= 0;
        };
    }

    /** Due date of a calendar plan; {@code null} for an hour-based plan. */
    public LocalDate nextDueDate() {
        return trigger == TriggerType.CALENDAR ? lastServiceDate.plusDays(intervalDays) : null;
    }

    /** Hours left before an hour-based plan is due (negative when overdue); {@code null} for a calendar plan. */
    public Double remainingHours() {
        return trigger == TriggerType.OPERATING_HOURS
                ? lastServiceHours + intervalHours - equipment.getOperatingHours()
                : null;
    }

    /** A work order was generated: the next occurrence is counted from now. */
    public void markServiceGenerated(LocalDate today) {
        this.lastServiceDate = today;
        this.lastServiceHours = equipment.getOperatingHours();
    }

    public void update(String title, String instructions, Integer interval, boolean active) {
        requirePositive(interval);
        this.title = Objects.requireNonNull(title);
        this.instructions = instructions;
        if (trigger == TriggerType.CALENDAR) {
            this.intervalDays = interval;
        } else {
            this.intervalHours = interval;
        }
        this.active = active;
    }

    private static void requirePositive(Integer interval) {
        if (interval == null || interval <= 0) {
            throw new IllegalArgumentException("The interval must be positive");
        }
    }

    public Equipment getEquipment() {
        return equipment;
    }

    public String getTitle() {
        return title;
    }

    public String getInstructions() {
        return instructions;
    }

    public TriggerType getTrigger() {
        return trigger;
    }

    public Integer getIntervalDays() {
        return intervalDays;
    }

    public Integer getIntervalHours() {
        return intervalHours;
    }

    public LocalDate getLastServiceDate() {
        return lastServiceDate;
    }

    public double getLastServiceHours() {
        return lastServiceHours;
    }

    public boolean isActive() {
        return active;
    }
}
