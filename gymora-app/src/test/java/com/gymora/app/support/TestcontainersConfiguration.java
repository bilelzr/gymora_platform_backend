package com.gymora.app.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        // Same major version as infra/docker-compose.yml
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }

    /** Tests never call the real Keycloak. */
    @Bean
    @Primary
    RecordingIdentityAdmin recordingIdentityAdmin() {
        return new RecordingIdentityAdmin();
    }
}
