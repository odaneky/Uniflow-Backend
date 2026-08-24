package com.university.lms.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The API document is generated from the controllers themselves — annotations and DTO shapes,
 * never hand-maintained — so it cannot drift from what the API actually accepts. This bean supplies
 * only what generation cannot infer: the document's identity and how a client authenticates.
 *
 * <p>Gated to {@code SYSTEM_ADMIN} in {@code SecurityConfig}: the document enumerates every
 * endpoint's shape, which is not something to hand to every authenticated caller by default.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI universityLmsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("UniFlow API")
                        .description("University Learning Management System — modular monolith")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
