package dev.atelierflow.common.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The current time is injected instead of calling {@code Instant.now()} directly, so tests can pin it
 * with {@code Clock.fixed(...)} and time-dependent rules (preventive due dates, KPIs) stay testable.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
