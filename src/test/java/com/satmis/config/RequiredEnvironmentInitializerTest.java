package com.satmis.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MissingRequiredPropertiesException;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiredEnvironmentInitializerTest {

    private final RequiredEnvironmentInitializer initializer = new RequiredEnvironmentInitializer();

    @Test
    void initialize_prodProfileWithMissingVariables_failsNamingThem() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("JWT_SECRET", "secret")
                .withProperty("DB_USERNAME", "satmis");
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> initializer.initialize(contextWith(environment)))
                .isInstanceOf(MissingRequiredPropertiesException.class)
                .satisfies(exception -> assertThat(((MissingRequiredPropertiesException) exception).getMissingRequiredProperties())
                        .containsExactlyInAnyOrder("DB_URL", "DB_PASSWORD", "CORS_ALLOWED_ORIGINS"));
    }

    @Test
    void initialize_prodProfileWithAllVariables_succeeds() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("JWT_SECRET", "secret")
                .withProperty("DB_URL", "jdbc:postgresql://db:5432/satmis")
                .withProperty("DB_USERNAME", "satmis")
                .withProperty("DB_PASSWORD", "password")
                .withProperty("CORS_ALLOWED_ORIGINS", "https://satmis.com");
        environment.setActiveProfiles("prod");

        assertThatCode(() -> initializer.initialize(contextWith(environment))).doesNotThrowAnyException();
    }

    @Test
    void initialize_devProfileWithoutDbPassword_fails() {
        MockEnvironment environment = new MockEnvironment().withProperty("JWT_SECRET", "secret");
        environment.setActiveProfiles("dev");

        assertThatThrownBy(() -> initializer.initialize(contextWith(environment)))
                .isInstanceOf(MissingRequiredPropertiesException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void initialize_noActiveProfile_requiresNothing() {
        assertThatCode(() -> initializer.initialize(contextWith(new MockEnvironment()))).doesNotThrowAnyException();
    }

    private static GenericApplicationContext contextWith(MockEnvironment environment) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);
        return context;
    }
}
