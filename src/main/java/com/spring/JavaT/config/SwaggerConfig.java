package com.spring.JavaT.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration.
 *
 * <p>Defines the {@code bearerAuth} security scheme so that the Swagger UI
 * "Authorize" button accepts a JWT and sends it as {@code Authorization: Bearer <token>}
 * on every request made from the UI.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title       = "Utility Billing System API",
                version     = "1.0",
                description = "Utility Billing System — customer management, meter readings, tariff configuration, "
                        + "bill generation, payments, notifications, and file uploads"
        )
)
@SecurityScheme(
        name   = "bearerAuth",
        type   = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class SwaggerConfig {
}
