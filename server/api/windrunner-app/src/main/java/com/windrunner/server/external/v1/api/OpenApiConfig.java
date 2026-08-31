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
                        .description("""
                                API-key authenticated endpoints for Windrunner integrations.

                                Send the key as `Authorization: Bearer <key>`. Each operation
                                requires the scope named in the API reference. Project-scoped
                                reads and writes also require the key owner to have access to
                                that project. Team and team-project directory endpoints are
                                organization-wide; audit-log endpoints require an admin or
                                superadmin API-key owner.

                                Collection endpoints use zero-based `page` and `size` parameters.
                                The maximum page size is 100 and pagination metadata is returned
                                in the standard `meta` response object. Search responses are
                                bounded by their `limit` parameter (maximum 100).
                                """))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SECURITY_SCHEME))
                .components(new Components().addSecuritySchemes(
                        API_KEY_SECURITY_SCHEME,
                        new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("API key")
                                .description("Use an active Windrunner API key. Create and revoke keys from My Account → API keys.")));
    }
}
