package dev.atelierflow.maintenance;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Checks the maintenance plans periodically (hourly by default, configurable with a cron expression).
 * The job is idempotent: running it twice creates no duplicate, so a restart or a manual run is safe.
 */
@Component
class PreventiveMaintenanceScheduler {

    private final MaintenancePlanService service;

    PreventiveMaintenanceScheduler(MaintenancePlanService service) {
        this.service = service;
    }

    @Scheduled(cron = "${atelierflow.maintenance.preventive-cron:0 0 * * * *}")
    void generateDueWorkOrders() {
        service.generateDueWorkOrders();
    }
}
