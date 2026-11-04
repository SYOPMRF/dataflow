package com.dataflow.backend.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.flywaydb.core.Flyway;

public final class DatabaseMigrationRunner {

    private DatabaseMigrationRunner() {
    }

    public static void main(String[] args) throws SQLException {

        String url = requireEnvironmentVariable(
                "SPRING_DATASOURCE_URL");

        String username = requireEnvironmentVariable(
                "SPRING_DATASOURCE_USERNAME");

        String password = requireEnvironmentVariable(
                "SPRING_DATASOURCE_PASSWORD");

        String runtimeUsername = requireEnvironmentVariable(
                "APP_DB_USER");

        String runtimePassword = requireEnvironmentVariable(
                "APP_DB_PASSWORD");

        DatabaseUserProvisioner provisioner = new DatabaseUserProvisioner(
                runtimeUsername,
                runtimePassword);

        // 1. Crear o actualizar el usuario runtime.
        try (Connection connection = DriverManager.getConnection(url, username, password)) {

            provisioner.provisionRole(connection);
        }

        // 2. Ejecutar las migraciones con el usuario administrativo.
        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .validateMigrationNaming(true)
                .load();

        flyway.migrate();

        // 3. Conceder permisos después de crear las tablas.
        try (Connection connection = DriverManager.getConnection(url, username, password)) {

            provisioner.grantRuntimePrivileges(connection);
        }

        System.out.println(
                "Database migrations and runtime user provisioning "
                        + "completed successfully.");
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