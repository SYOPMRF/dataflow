package com.dataflow.backend.migration;

import org.flywaydb.core.Flyway;

public final class DatabaseMigrationRunner {

    private DatabaseMigrationRunner() {
    }

    public static void main(String[] args) {
        String url = requireEnvironmentVariable("SPRING_DATASOURCE_URL");
        String username = requireEnvironmentVariable("SPRING_DATASOURCE_USERNAME");
        String password = requireEnvironmentVariable("SPRING_DATASOURCE_PASSWORD");

        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .validateMigrationNaming(true)
                .load();

        flyway.migrate();

        System.out.println("Database migrations completed successfully.");
    }

    private static String requireEnvironmentVariable(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required environment variable is missing: " + name);
        }

        return value;
    }
}