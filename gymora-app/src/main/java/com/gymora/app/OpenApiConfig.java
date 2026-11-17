package com.gymora.app;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI description at /v3/api-docs, Swagger UI at /swagger-ui.html. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER = "keycloak";

    @Bean
    OpenAPI gymoraOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("GYMORA API")
                        .version("v1")
                        .description("""
                                Multi-tenant gym management API. Authenticate with a Keycloak access token from \
                                the `gymora` realm. The gym (tenant) always comes from the token, never from the request. \
                                Error messages follow the Accept-Language header (fr, ar, en)."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
