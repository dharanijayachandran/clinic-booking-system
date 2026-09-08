package com.dharanijayachandran.clinicbooking.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Every integration test runs against a real Postgres in a container, not
 * H2 — H2 doesn't have Postgres's row-locking (SELECT ... FOR UPDATE)
 * semantics, and Phase 3's concurrency test only proves anything if the
 * database underneath it actually is Postgres.
 *
 * One container is shared across all test classes that extend this (started
 * once, reused) to keep the suite fast as it grows.
 */
@SpringBootTest
@Testcontainers
@ExtendWith(org.springframework.test.context.junit.jupiter.SpringExtension.class)
public abstract class PostgresTestBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("clinic")
                    .withUsername("clinic")
                    .withPassword("clinic");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
