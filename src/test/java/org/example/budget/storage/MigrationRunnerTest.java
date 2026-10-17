package org.example.budget.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MigrationRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void appliesTheAppMigrationsToAFreshDatabase() throws SQLException {
        try (Connection connection = connect()) {
            MigrationRunner runner = MigrationRunner.forApp();

            assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), runner.migrate(connection));
            assertEquals(8, runner.currentVersion(connection));
        }
    }

    @Test
    void appliesNothingOnSecondRun() throws SQLException {
        MigrationRunner runner = MigrationRunner.forApp();
        try (Connection connection = connect()) {
            runner.migrate(connection);
        }
        try (Connection connection = connect()) {
            assertEquals(List.of(), runner.migrate(connection));
            assertEquals(8, runner.currentVersion(connection));
        }
    }

    @Test
    void appliesScriptsInNumericOrder() throws SQLException {
        MigrationRunner runner = new MigrationRunner("org/example/budget/storage/ordering");
        try (Connection connection = connect()) {
            assertEquals(List.of(1, 2, 10), runner.migrate(connection));
            assertEquals(10, runner.currentVersion(connection));
        }
    }

    @Test
    void aFreshDatabaseIsAtVersionZero() throws SQLException {
        try (Connection connection = connect()) {
            assertEquals(0, MigrationRunner.forApp().currentVersion(connection));
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve("test.db"));
    }
}
