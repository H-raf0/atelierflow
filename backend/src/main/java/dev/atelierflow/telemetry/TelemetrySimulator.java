package dev.atelierflow.telemetry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Drives the simulation at a fixed pace; only present when {@code atelierflow.telemetry.enabled=true}. */
@Component
@ConditionalOnProperty(name = "atelierflow.telemetry.enabled", havingValue = "true")
class TelemetrySimulator {

    private final TelemetryService service;

    TelemetrySimulator(TelemetryService service) {
        this.service = service;
    }

    /** fixedDelay (not fixedRate): a slow tick never overlaps with the next one. */
    @Scheduled(fixedDelayString = "${atelierflow.telemetry.interval:30s}",
            initialDelayString = "${atelierflow.telemetry.interval:30s}")
    void tick() {
        service.tick();
    }
}
