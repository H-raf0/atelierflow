package dev.atelierflow.telemetry;

import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TelemetryProperties.class)
class TelemetryConfig {

    /**
     * Injected rather than created inline, so tests can use a deterministic generator.
     * SplittableRandom lives in java.base; {@code RandomGenerator.getDefault()} needs the jdk.random
     * module, which slim JRE images (such as the Docker runtime image) do not ship.
     */
    @Bean
    RandomGenerator telemetryRandom() {
        return new SplittableRandom();
    }
}
