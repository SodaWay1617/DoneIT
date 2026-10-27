package com.doneit.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class IntegrationTestSupport {

    private static final String EXTERNAL = System.getProperty("doneit.test.jdbc-url", System.getenv("DONEIT_TEST_JDBC_URL"));
    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        if (EXTERNAL == null || EXTERNAL.isBlank()) {
            POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("doneit_test").withUsername("doneit").withPassword("doneit");
            POSTGRES.start();
        } else {
            // Tests truncate tables. Never accept an arbitrary dev/production database here.
            java.net.URI uri = java.net.URI.create(EXTERNAL.substring("jdbc:".length()));
            if (!EXTERNAL.startsWith("jdbc:postgresql://") || !java.util.Set.of("localhost","127.0.0.1","[::1]").contains(uri.getHost())
                    || uri.getPath() == null || !uri.getPath().matches("/[A-Za-z][A-Za-z0-9_]*_test")
                    || uri.getQuery() != null || uri.getUserInfo() != null)
                throw new IllegalArgumentException("External tests require a loopback PostgreSQL database ending in _test without URL options");
            POSTGRES = null;
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES == null ? EXTERNAL : POSTGRES.getJdbcUrl());
        registry.add("spring.datasource.username", () -> POSTGRES == null ? System.getenv().getOrDefault("DONEIT_TEST_DB_USER","doneit") : POSTGRES.getUsername());
        registry.add("spring.datasource.password", () -> POSTGRES == null ? System.getenv().getOrDefault("DONEIT_TEST_DB_PASSWORD","doneit") : POSTGRES.getPassword());
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
