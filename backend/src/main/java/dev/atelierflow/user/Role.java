package dev.atelierflow.user;

/** The three profiles of the application (UI labels: Administrateur, Responsable, Technicien). */
public enum Role {
    /** Manages the fleet and users; has every permission. */
    ADMIN,
    /** Maintenance manager: creates, plans and assigns work orders. */
    MANAGER,
    /** Carries out the work orders assigned to them. */
    TECHNICIAN
}
