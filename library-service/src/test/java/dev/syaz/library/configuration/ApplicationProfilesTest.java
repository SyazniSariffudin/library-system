package dev.syaz.library.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationProfilesTest {
    @Test
    void defaultProfileUsesTheExternalDatabaseWithoutStartingDocker() {
        MockEnvironment environment = load(new MockEnvironment());

        assertThat(environment.getActiveProfiles()).isEmpty();
        assertThat(environment.getDefaultProfiles()).containsExactly("default");
        assertThat(environment.getProperty("spring.docker.compose.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
        assertThat(environment.getProperty("logging.structured.ecs.service.environment")).isEqualTo("default");
        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://localhost:5432/library");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("library");
        assertThat(environment.getProperty("spring.datasource.hikari.connection-init-sql")).isEqualTo("SET lock_timeout = '5s'");
    }

    @Test
    void defaultDatabaseSettingsCanBeOverridden() {
        MockEnvironment environment = load(new MockEnvironment()
                .withProperty("DB_URL", "jdbc:postgresql://database.example:5432/custom")
                .withProperty("DB_USERNAME", "custom-user")
                .withProperty("DB_PASSWORD", "custom-password")
                .withProperty("HTTP_LOGGING_ENABLED", "false"));

        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://database.example:5432/custom");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("custom-user");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("custom-password");
        assertThat(environment.getProperty("library.http-logging.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.docker.compose.enabled", Boolean.class)).isFalse();
    }

    @Test
    void localProfileStartsOnlyPostgresAndKeepsSharedSettings() throws Exception {
        MockEnvironment environment = load(new MockEnvironment().withProperty("spring.profiles.active", "local"));

        assertThat(environment.getActiveProfiles()).containsExactly("local");
        assertThat(environment.getProperty("spring.docker.compose.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
        assertThat(environment.getProperty("logging.structured.ecs.service.environment")).isEqualTo("local");
        assertThat(environment.getProperty("spring.docker.compose.lifecycle-management")).isEqualTo("start-and-stop");
        assertThat(environment.getProperty("spring.datasource.url")).isNull();
        assertThat(environment.getProperty("spring.datasource.hikari.connection-init-sql")).isEqualTo("SET lock_timeout = '5s'");

        Path composePath = Path.of(environment.getRequiredProperty("spring.docker.compose.file"));
        assertThat(Files.isRegularFile(composePath)).as("local Compose file resolves from the service module").isTrue();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("local-compose", new FileSystemResource(composePath));
        EnumerablePropertySource<?> compose = (EnumerablePropertySource<?>) sources.get(0);
        List<String> services = Arrays.stream(compose.getPropertyNames())
                .filter(name -> name.startsWith("services."))
                .map(name -> name.split("\\.")[1]).distinct().toList();

        assertThat(services).containsExactly("postgres");
        assertThat(compose.getProperty("services.postgres.image")).isEqualTo("postgres:18.7-alpine");
        assertThat(compose.getProperty("services.postgres.ports[0].published")).isNull();
        assertThat(compose.getProperty("services.postgres.ports[0].host_ip")).isEqualTo("127.0.0.1");
    }

    private MockEnvironment load(MockEnvironment environment) {
        environment.withProperty("spring.config.location", "classpath:/");
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return environment;
    }
}
