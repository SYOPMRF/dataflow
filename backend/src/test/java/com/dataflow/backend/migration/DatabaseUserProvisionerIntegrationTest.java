package com.dataflow.backend.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class DatabaseUserProvisionerIntegrationTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/dataflow_test";

    private static final String ADMIN_USER = "poysmon";
    private static final String ADMIN_PASSWORD = "";

    @Test
    void shouldProvisionRestrictedRuntimeUserAndGrantExpectedPrivileges()
            throws Exception {

        String suffix = UUID.randomUUID().toString().replace("-", "");
        String runtimeUsername = "dataflow_test_" + suffix;
        String runtimePassword = "IntegrationTest-" + suffix;
        String applicationTable = "provision_test_" + suffix;
        String futureTable = "future_test_" + suffix;
        String groupUsername = "dataflow_group_" + suffix;

        boolean historyTableCreated = false;

        try (Connection adminConnection = DriverManager.getConnection(
                JDBC_URL, ADMIN_USER, ADMIN_PASSWORD)) {

            try {
                historyTableCreated = createHistoryTableIfMissing(
                        adminConnection);

                createApplicationTable(
                        adminConnection, applicationTable);

                DatabaseUserProvisioner provisioner = new DatabaseUserProvisioner(
                        runtimeUsername, runtimePassword);

                provisioner.provisionRole(adminConnection);

                assertRuntimeRoleIsRestricted(
                        adminConnection, runtimeUsername);

                provisioner.grantRuntimePrivileges(adminConnection);

                // Simular una membresía que no debe conservar el usuario runtime.
                executeIdentifierStatement(
                        adminConnection,
                        "CREATE ROLE %s",
                        groupUsername);

                executeIdentifierStatement(
                        adminConnection,
                        "GRANT %s TO %s",
                        groupUsername,
                        runtimeUsername);

                assertRoleMembershipExists(
                        adminConnection,
                        groupUsername,
                        runtimeUsername);

                assertTablePrivileges(
                        adminConnection,
                        runtimeUsername,
                        applicationTable,
                        true);

                assertTablePrivileges(
                        adminConnection,
                        runtimeUsername,
                        "flyway_schema_history",
                        false);

                createFutureTable(adminConnection, futureTable);

                assertTablePrivileges(
                        adminConnection,
                        runtimeUsername,
                        futureTable,
                        true);

                verifyRuntimeCanInsert(
                        runtimeUsername,
                        runtimePassword,
                        applicationTable);

                verifyRuntimeCanInsert(
                        runtimeUsername,
                        runtimePassword,
                        futureTable);

                // Comprobar que el provisionamiento puede repetirse.
                DatabaseUserProvisioner updatedProvisioner = new DatabaseUserProvisioner(
                        runtimeUsername,
                        runtimePassword + "-updated");

                updatedProvisioner.provisionRole(adminConnection);

                assertRoleMembershipDoesNotExist(
                        adminConnection,
                        groupUsername,
                        runtimeUsername);

                verifyRuntimeCanConnect(
                        runtimeUsername,
                        runtimePassword + "-updated");

            } finally {
                cleanup(
                        adminConnection,
                        runtimeUsername,
                        groupUsername,
                        applicationTable,
                        futureTable,
                        historyTableCreated);
            }
        }
    }

    private boolean createHistoryTableIfMissing(Connection connection)
            throws Exception {

        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM pg_tables
                            WHERE schemaname = 'public'
                              AND tablename = 'flyway_schema_history'
                        )
                        """)) {

            result.next();

            if (result.getBoolean(1)) {
                return false;
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE public.flyway_schema_history (
                        installed_rank INTEGER PRIMARY KEY
                    )
                    """);
        }

        return true;
    }

    private void createApplicationTable(
            Connection connection,
            String tableName) throws Exception {

        executeIdentifierStatement(
                connection,
                """
                        CREATE TABLE public.%s (
                            id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                            value TEXT NOT NULL
                        )
                        """,
                tableName);
    }

    private void createFutureTable(
            Connection connection,
            String tableName) throws Exception {

        createApplicationTable(connection, tableName);
    }

    private void assertRuntimeRoleIsRestricted(
            Connection connection,
            String username) throws Exception {

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT rolsuper, rolcreatedb, rolcreaterole
                FROM pg_roles
                WHERE rolname = ?
                """)) {

            statement.setString(1, username);

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertFalse(result.getBoolean("rolsuper"));
                assertFalse(result.getBoolean("rolcreatedb"));
                assertFalse(result.getBoolean("rolcreaterole"));
            }
        }
    }

    private void assertTablePrivileges(
            Connection connection,
            String username,
            String tableName,
            boolean expected) throws Exception {

        String qualifiedTable = "public." + tableName;

        for (String privilege : new String[] {
                "SELECT", "INSERT", "UPDATE", "DELETE" }) {

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT has_table_privilege(?, ?, ?)")) {

                statement.setString(1, username);
                statement.setString(2, qualifiedTable);
                statement.setString(3, privilege);

                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertTrue(
                            result.getBoolean(1) == expected,
                            "Unexpected " + privilege
                                    + " privilege on " + qualifiedTable);
                }
            }
        }
    }

    private void verifyRuntimeCanInsert(
            String username,
            String password,
            String tableName) throws Exception {

        try (Connection runtimeConnection = DriverManager.getConnection(
                JDBC_URL, username, password);
                PreparedStatement statement = runtimeConnection.prepareStatement(
                        "INSERT INTO public."
                                + tableName
                                + " (value) VALUES (?)")) {

            statement.setString(1, "integration-test");
            statement.executeUpdate();
        }
    }

    private void verifyRuntimeCanConnect(
            String username,
            String password) throws Exception {

        try (Connection ignored = DriverManager.getConnection(
                JDBC_URL, username, password)) {
            assertTrue(ignored.isValid(2));
        }
    }

    private void cleanup(
            Connection connection,
            String username,
            String groupUsername,
            String applicationTable,
            String futureTable,
            boolean historyTableCreated) throws Exception {

        String revokeDefaults = """
                SELECT format(
                    'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public '
                    || 'REVOKE SELECT, INSERT, UPDATE, DELETE ON TABLES FROM %I',
                    current_user, ?
                )
                """;

        try (PreparedStatement statement = connection.prepareStatement(revokeDefaults)) {

            statement.setString(1, username);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    try (Statement execute = connection.createStatement()) {
                        execute.execute(result.getString(1));
                    }
                }
            }
        }

        dropTable(connection, applicationTable);
        dropTable(connection, futureTable);

        if (historyTableCreated) {
            dropTable(connection, "flyway_schema_history");
        }

        // Revocar los privilegios restantes sobre la base y el esquema.
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT format('DROP OWNED BY %I', ?)")) {

            statement.setString(1, username);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    try (Statement execute = connection.createStatement()) {
                        execute.execute(result.getString(1));
                    }
                }
            }
        }

        // Ahora el rol ya no tiene dependencias de privilegios.
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT format('DROP ROLE IF EXISTS %I', ?)")) {

            statement.setString(1, username);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    try (Statement execute = connection.createStatement()) {
                        execute.execute(result.getString(1));
                    }
                }
            }
        }
        executeIdentifierStatement(
                connection,
                "DROP ROLE IF EXISTS %s",
                groupUsername);
    }

    private void dropTable(
            Connection connection,
            String tableName) throws Exception {

        executeIdentifierStatement(
                connection,
                "DROP TABLE IF EXISTS public.%s",
                tableName);
    }

    private void executeIdentifierStatement(
            Connection connection,
            String template,
            String... identifiers) throws Exception {

        String[] quotedIdentifiers = new String[identifiers.length];

        for (int i = 0; i < identifiers.length; i++) {
            String identifier = identifiers[i];

            if (!identifier.matches("[a-zA-Z_][a-zA-Z0-9_]{0,62}")) {
                throw new IllegalArgumentException(
                        "Invalid test identifier.");
            }

            quotedIdentifiers[i] = "\"" + identifier + "\"";
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute(template.formatted(
                    (Object[]) quotedIdentifiers));
        }
    }

    private void assertRoleMembershipExists(
            Connection connection,
            String grantedRole,
            String memberRole) throws Exception {

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1
                    FROM pg_auth_members membership
                    JOIN pg_roles granted
                        ON granted.oid = membership.roleid
                    JOIN pg_roles member
                        ON member.oid = membership.member
                    WHERE granted.rolname = ?
                      AND member.rolname = ?
                )
                """)) {

            statement.setString(1, grantedRole);
            statement.setString(2, memberRole);

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertTrue(result.getBoolean(1));
            }
        }
    }

    private void assertRoleMembershipDoesNotExist(
            Connection connection,
            String grantedRole,
            String memberRole) throws Exception {

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1
                    FROM pg_auth_members membership
                    JOIN pg_roles granted
                        ON granted.oid = membership.roleid
                    JOIN pg_roles member
                        ON member.oid = membership.member
                    WHERE granted.rolname = ?
                      AND member.rolname = ?
                )
                """)) {

            statement.setString(1, grantedRole);
            statement.setString(2, memberRole);

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertFalse(
                        result.getBoolean(1),
                        "Runtime role must not retain unexpected memberships.");
            }
        }
    }
}