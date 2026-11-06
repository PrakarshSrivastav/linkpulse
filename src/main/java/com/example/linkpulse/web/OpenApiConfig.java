package com.example.linkpulse.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Swagger UI: /swagger-ui.html, raw spec: /v3/api-docs.
// The "bearerAuth" scheme gives Swagger UI its Authorize button: paste the accessToken from /api/auth/login.
@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI linkPulseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("LinkPulse API")
                        .version("v1")
                        .description("URL shortener with a Redis cache, JWT auth, per-user links and click analytics. "
                                + "Log in with /api/auth/login, then click Authorize and paste the accessToken.")
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
