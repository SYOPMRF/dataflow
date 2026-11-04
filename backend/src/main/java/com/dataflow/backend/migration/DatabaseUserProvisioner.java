package com.dataflow.backend.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class DatabaseUserProvisioner {

    private static final Pattern VALID_IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]{0,62}");

    private final String runtimeUsername;
    private final String runtimePassword;

    public DatabaseUserProvisioner(
            String runtimeUsername,
            String runtimePassword) {

        this.runtimeUsername = validateIdentifier(runtimeUsername);

        if (runtimePassword == null || runtimePassword.isBlank()) {
            throw new IllegalArgumentException(
                    "Runtime database password must not be blank.");
        }

        this.runtimePassword = runtimePassword;
    }

    public void provisionRole(Connection connection) throws SQLException {
        String currentUser = getCurrentUser(connection);

        if (currentUser.equals(runtimeUsername)) {
            throw new IllegalArgumentException(
                    "Migration and runtime database users must be different.");
        }

        createRoleIfMissing(connection);
        revokeRoleMemberships(connection);
        configureRole(connection);
    }

    private void revokeRoleMemberships(Connection connection)
            throws SQLException {

        List<String> revokeStatements = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT format(
                    'REVOKE %I FROM %I',
                    granted.rolname,
                    member.rolname
                )
                FROM pg_auth_members membership
                JOIN pg_roles granted
                    ON granted.oid = membership.roleid
                JOIN pg_roles member
                    ON member.oid = membership.member
                WHERE member.rolname = ?
                """)) {

            statement.setString(1, runtimeUsername);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    revokeStatements.add(result.getString(1));
                }
            }
        }

        try (Statement statement = connection.createStatement()) {
            for (String sql : revokeStatements) {
                statement.execute(sql);
            }
        }
    }

    public void grantRuntimePrivileges(Connection connection)
            throws SQLException {

        grantDatabasePermissions(connection);
        grantSchemaPermissions(connection);
        grantTablePermissions(connection);
        configureDefaultPrivileges(connection);
    }

    private void createRoleIfMissing(Connection connection)
            throws SQLException {

        boolean roleExists;

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM pg_roles WHERE rolname = ?")) {

            statement.setString(1, runtimeUsername);

            try (ResultSet result = statement.executeQuery()) {
                roleExists = result.next();
            }
        }

        if (!roleExists) {
            executeFormattedSql(
                    connection,
                    "SELECT format('CREATE ROLE %I LOGIN', ?)",
                    runtimeUsername);
        }
    }

    private void configureRole(Connection connection)
            throws SQLException {

        executeFormattedSql(
                connection,
                """
                        SELECT format(
                            'ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB '
                            || 'NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD %L',
                            ?, ?
                        )
                        """,
                runtimeUsername,
                runtimePassword);
    }

    private void grantDatabasePermissions(Connection connection)
            throws SQLException {

        executeFormattedSql(
                connection,
                """
                        SELECT format(
                            'GRANT CONNECT ON DATABASE %I TO %I',
                            current_database(), ?
                        )
                        """,
                runtimeUsername);
    }

    private void grantSchemaPermissions(Connection connection)
            throws SQLException {

        executeFormattedSql(
                connection,
                """
                        SELECT format(
                            'GRANT USAGE ON SCHEMA public TO %I',
                            ?
                        )
                        """,
                runtimeUsername);
    }

    private void grantTablePermissions(Connection connection)
            throws SQLException {

        List<String> grantStatements = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(
                """
                        SELECT format(
                            'GRANT SELECT, INSERT, UPDATE, DELETE '
                            || 'ON TABLE %I.%I TO %I',
                            schemaname, tablename, ?
                        )
                        FROM pg_tables
                        WHERE schemaname = 'public'
                          AND tablename <> 'flyway_schema_history'
                        """)) {

            statement.setString(1, runtimeUsername);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    grantStatements.add(result.getString(1));
                }
            }
        }

        try (Statement statement = connection.createStatement()) {
            for (String sql : grantStatements) {
                statement.execute(sql);
            }
        }
    }

    private void configureDefaultPrivileges(Connection connection)
            throws SQLException {

        executeFormattedSql(
                connection,
                """
                        SELECT format(
                            'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA %I '
                            || 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I',
                            current_user, 'public', ?
                        )
                        """,
                runtimeUsername);
    }

    private String getCurrentUser(Connection connection)
            throws SQLException {

        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT current_user")) {

            if (!result.next()) {
                throw new SQLException(
                        "Unable to determine migration database user.");
            }

            return result.getString(1);
        }
    }

    private static void executeFormattedSql(
            Connection connection,
            String formatQuery,
            String... parameters) throws SQLException {

        String sql;

        try (PreparedStatement statement = connection.prepareStatement(formatQuery)) {

            for (int i = 0; i < parameters.length; i++) {
                statement.setString(i + 1, parameters[i]);
            }

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException(
                            "Unable to generate database provisioning SQL.");
                }

                sql = result.getString(1);
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String validateIdentifier(String identifier) {

        if (identifier == null
                || !VALID_IDENTIFIER.matcher(identifier).matches()) {

            throw new IllegalArgumentException(
                    "Invalid PostgreSQL role identifier.");
        }

        return identifier;
    }
}