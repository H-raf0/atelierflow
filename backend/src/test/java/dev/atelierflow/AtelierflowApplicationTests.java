package dev.atelierflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.atelierflow.support.IntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class AtelierflowApplicationTests extends IntegrationTest {

    /**
     * Also exports the OpenAPI contract to {@code target/openapi.json}; the frontend generates its typed
     * API client from it ({@code npm run generate:api}), so the contract always comes from the real code.
     */
    @Test
    void contextStartsWithFlywaySchemaAndPublishesOpenApi() throws Exception {
        MvcTestResult result = anonymous().get().uri("/v3/api-docs").exchange();

        assertThat(result).hasStatusOk()
                .bodyJson().extractingPath("$.info.title").isEqualTo("AtelierFlow API");
        Files.writeString(Path.of("target", "openapi.json"), result.getResponse().getContentAsString());
    }

    @Test
    void healthEndpointIsUp() {
        assertThat(anonymous().get().uri("/actuator/health"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("UP");
    }
}
