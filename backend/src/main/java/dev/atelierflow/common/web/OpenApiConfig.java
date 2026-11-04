package dev.atelierflow.common.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadata of the generated OpenAPI document (served at /v3/api-docs, UI at /swagger-ui.html). */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    OpenAPI atelierFlowOpenApi() {
        return new OpenAPI().info(new Info()
                .title("AtelierFlow API")
                .version("v1")
                .description("CMMS for an industrial workshop: equipment fleet, work orders, preventive maintenance, KPIs."));
    }
}
