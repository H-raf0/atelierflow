package dev.atelierflow.telemetry;

import java.util.random.RandomGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TelemetryProperties.class)
class TelemetryConfig {

    /** Injected rather than created inline, so tests can use a deterministic generator. */
    @Bean
    RandomGenerator telemetryRandom() {
        return RandomGenerator.getDefault();
    }
}
