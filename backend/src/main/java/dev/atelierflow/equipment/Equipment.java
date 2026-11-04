package dev.atelierflow.equipment;

import dev.atelierflow.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A machine of the workshop fleet.
 *
 * <p>The entity has no public setters: state only changes through intention-revealing methods, so the
 * invariants (immutable code, monotonic hour counter) are enforced in one place.
 */
@Entity
@Table(name = "equipment")
public class Equipment extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 60)
    private String workshop;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Criticality criticality;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EquipmentStatus status = EquipmentStatus.OPERATIONAL;

    /** Cumulated running time, fed by the telemetry; only ever increases. */
    @Column(nullable = false)
    private double operatingHours;

    private LocalDate commissionedOn;

    /** Required by JPA. */
    protected Equipment() {
    }

    public Equipment(String code, String name, String workshop, Criticality criticality,
                     double operatingHours, LocalDate commissionedOn) {
        if (operatingHours < 0) {
            throw new IllegalArgumentException("operatingHours must be positive");
        }
        this.code = Objects.requireNonNull(code);
        this.name = Objects.requireNonNull(name);
        this.workshop = Objects.requireNonNull(workshop);
        this.criticality = Objects.requireNonNull(criticality);
        this.operatingHours = operatingHours;
        this.commissionedOn = commissionedOn;
    }

    public void updateDetails(String name, String workshop, Criticality criticality, LocalDate commissionedOn) {
        this.name = Objects.requireNonNull(name);
        this.workshop = Objects.requireNonNull(workshop);
        this.criticality = Objects.requireNonNull(criticality);
        this.commissionedOn = commissionedOn;
    }

    public void addOperatingHours(double hours) {
        if (hours < 0) {
            throw new IllegalArgumentException("Operating hours can only increase");
        }
        this.operatingHours += hours;
    }

    public void changeStatus(EquipmentStatus status) {
        this.status = Objects.requireNonNull(status);
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getWorkshop() {
        return workshop;
    }

    public Criticality getCriticality() {
        return criticality;
    }

    public EquipmentStatus getStatus() {
        return status;
    }

    public double getOperatingHours() {
        return operatingHours;
    }

    public LocalDate getCommissionedOn() {
        return commissionedOn;
    }
}
