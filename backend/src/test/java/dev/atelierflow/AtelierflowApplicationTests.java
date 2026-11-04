package dev.atelierflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.atelierflow.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class AtelierflowApplicationTests extends IntegrationTest {

    @Test
    void contextStartsWithFlywaySchemaAndPublishesOpenApi() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.info.title").isEqualTo("AtelierFlow API");
    }

    @Test
    void healthEndpointIsUp() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("UP");
    }
}
