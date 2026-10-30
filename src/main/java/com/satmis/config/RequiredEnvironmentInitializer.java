package com.satmis.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

// Spring keeps an unresolved ${VAR} as literal text, so a missing variable would only surface later
// as a misleading error (e.g. "password authentication failed"). Failing here names the variable.
public class RequiredEnvironmentInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Map<String, List<String>> REQUIRED_BY_PROFILE = Map.of(
            "dev", List.of("JWT_SECRET", "DB_PASSWORD"),
            "prod", List.of("JWT_SECRET", "DB_URL", "DB_USERNAME", "DB_PASSWORD", "CORS_ALLOWED_ORIGINS"));

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment environment = context.getEnvironment();
        String[] required = Arrays.stream(environment.getActiveProfiles())
                .flatMap(profile -> REQUIRED_BY_PROFILE.getOrDefault(profile, List.of()).stream())
                .distinct()
                .toArray(String[]::new);
        environment.setRequiredProperties(required);
        environment.validateRequiredProperties();
    }
}
