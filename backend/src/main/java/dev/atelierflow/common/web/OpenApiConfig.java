package dev.atelierflow.common.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.ArrayList;
import java.util.Arrays;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.PropertyCustomizer;
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

    /**
     * Response bodies always contain every field (Jackson writes nulls explicitly), so all their
     * properties are declared required; the ones that can be null carry {@code @Schema(nullable = true)}.
     * The generated TypeScript models are then exact: {@code field: T} or {@code field: T | null},
     * never an ambiguous optional {@code field?: T}. Request schemas keep their Bean Validation rules.
     */
    @Bean
    OpenApiCustomizer responsePropertiesAreRequired() {
        return openApi -> openApi.getComponents().getSchemas().forEach((name, schema) -> {
            // a component is never null by itself; see nullableReferences() for nullable properties
            schema.setNullable(null);
            if (!name.endsWith("Request") && schema.getProperties() != null) {
                schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
            }
        });
    }

    /**
     * In OpenAPI 3.0 a {@code $ref} cannot carry {@code nullable}: springdoc would instead mark the
     * referenced component itself as nullable (making every use of it nullable). A nullable property
     * pointing to another schema is therefore expressed as {@code allOf: [$ref], nullable: true}.
     */
    @Bean
    PropertyCustomizer nullableReferences() {
        return (property, type) -> {
            boolean nullable = type.getCtxAnnotations() != null && Arrays.stream(type.getCtxAnnotations())
                    .anyMatch(a -> a instanceof io.swagger.v3.oas.annotations.media.Schema s && s.nullable());
            if (nullable && property.get$ref() != null) {
                return new ComposedSchema().addAllOfItem(new Schema<>().$ref(property.get$ref())).nullable(true);
            }
            return property;
        };
    }
}
