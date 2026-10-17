package org.example.budget.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTest {

    @TempDir
    Path tempDir;

    @Test
    void firstOpenCreatesTheDataDirectoryAndAMigratedDatabase() throws SQLException {
        Path dataDir = tempDir.resolve("AppData").resolve("BudgetOrganizer");

        Database database = Database.openIn(dataDir);

        assertEquals(dataDir.resolve("budget.db"), database.file());
        assertTrue(Files.isRegularFile(database.file()));
        try (Connection connection = database.connect()) {
            assertEquals(5, MigrationRunner.forApp().currentVersion(connection));
        }
    }

    @Test
    void reopeningAnExistingDatabaseKeepsItsVersion() throws SQLException {
        Database.openIn(tempDir);
        Database reopened = Database.openIn(tempDir);

        try (Connection connection = reopened.connect()) {
            assertEquals(5, MigrationRunner.forApp().currentVersion(connection));
        }
    }
}
