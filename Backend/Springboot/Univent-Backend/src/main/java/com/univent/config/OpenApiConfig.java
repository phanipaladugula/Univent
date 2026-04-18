package com.univent.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String JWT_SCHEME = "bearer-jwt";

    @Bean
    public OpenAPI univentOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Univent API")
                        .description("""
                                REST API for the Univent platform. Click **Authorize**, choose bearer-jwt, \
                                and paste an access token for protected routes (use an admin token for `/api/v1/admin/**`).\
                                Public routes such as `/api/v1/auth/**` and many `GET` APIs do not require a token.\
                                """)
                        .version("v1")
                        .contact(new Contact().name("Univent")))
                .components(new Components()
                        .addSecuritySchemes(JWT_SCHEME,
                                new SecurityScheme()
                                        .name(JWT_SCHEME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT from `POST /api/v1/auth/verify-otp` or `POST /api/v1/auth/refresh`")));
    }
}
