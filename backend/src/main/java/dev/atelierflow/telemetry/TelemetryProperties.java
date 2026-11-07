package dev.atelierflow.telemetry;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the telemetry simulator ({@code atelierflow.telemetry.*}).
 *
 * @param enabled            off by default; switched on by the demo profile
 * @param interval           real time between two simulated readings
 * @param hoursPerTick       operating hours added to each running machine per reading (time is accelerated)
 * @param failureProbability base probability that a running machine breaks down at a given reading,
 *                           weighted by its criticality (critical machines are more solicited)
 */
@ConfigurationProperties("atelierflow.telemetry")
public record TelemetryProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("30s") Duration interval,
        @DefaultValue("0.5") double hoursPerTick,
        @DefaultValue("0.002") double failureProbability) {
}
