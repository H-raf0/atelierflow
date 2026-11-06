package dev.atelierflow.common.time;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} jobs. Can be switched off ({@code atelierflow.scheduling.enabled=false}),
 * which the integration tests do so that background jobs never interfere with test data.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "atelierflow.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig {
}
