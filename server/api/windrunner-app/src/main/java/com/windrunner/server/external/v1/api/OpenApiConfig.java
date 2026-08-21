package com.windrunner.server.external.v1.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String API_KEY_SECURITY_SCHEME = "ApiKeyBearerAuth";

    @Bean
    public OpenAPI windrunnerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Windrunner API")
                        .version("v1")
                        .description("API-key authenticated endpoints for Windrunner integrations."))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SECURITY_SCHEME))
                .components(new Components().addSecuritySchemes(
                        API_KEY_SECURITY_SCHEME,
                        new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("API key")));
    }
}
