package dev.atelierflow.common.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata of the generated OpenAPI document (served at /v3/api-docs, UI at /swagger-ui.html).
 * The bearer scheme adds an "Authorize" button to Swagger UI: paste the access token from /api/auth/login.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI atelierFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AtelierFlow API")
                        .version("v1")
                        .description("CMMS for an industrial workshop: equipment fleet, work orders, "
                                + "preventive maintenance, KPIs."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
