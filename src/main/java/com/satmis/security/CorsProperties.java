package com.satmis.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        @DefaultValue("http://localhost:5173") List<String> allowedOrigins
) {
}
