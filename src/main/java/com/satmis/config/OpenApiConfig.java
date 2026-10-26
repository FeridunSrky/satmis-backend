package com.satmis.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

// Adds an "Authorize" button to Swagger UI so protected endpoints can be called with a JWT
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "satmis.com API", version = "v1"),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
@SecurityScheme(name = OpenApiConfig.BEARER_SCHEME, type = SecuritySchemeType.HTTP,
        scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";
}
