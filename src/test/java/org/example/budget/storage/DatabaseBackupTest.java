package org.example.budget.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseBackupTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 14, 3, 22);

    @TempDir
    Path tempDir;

    @Test
    void theBackupIsATimestampedCopyInTheChosenFolder() throws SQLException, IOException {
        Database database = Database.openIn(tempDir.resolve("data"));
        Path folder = tempDir.resolve("backups");

        Path backup = database.backupTo(folder, NOW);

        assertEquals(folder.resolve("budget-2026-10-18_14-03-22.db"), backup);
        assertTrue(Files.isRegularFile(backup));
    }

    @Test
    void theBackupOpensAsAValidDatabaseWithTheSameData() throws SQLException, IOException {
        Database database = Database.openIn(tempDir.resolve("data"));
        new SqliteSettingsRepository(database).save("language", "cs");
        Path backup = database.backupTo(tempDir.resolve("backups"), NOW);

        try (Connection copy = DriverManager.getConnection("jdbc:sqlite:" + backup);
             Connection original = database.connect()) {
            assertEquals(MigrationRunner.forApp().currentVersion(original),
                    MigrationRunner.forApp().currentVersion(copy));
        }
        Database copy = Database.openIn(tempDir.resolve("restored"));
        Files.copy(backup, copy.file(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertEquals(Optional.of("cs"), new SqliteSettingsRepository(Database.openIn(tempDir.resolve("restored")))
                .find("language"));
    }

    @Test
    void theBackupIsTakenFromTheDataEvenWhileTheDatabaseIsOpen() throws SQLException, IOException {
        Database database = Database.openIn(tempDir.resolve("data"));
        try (Connection open = database.connect()) {
            new SqliteSettingsRepository(database).save("theme", "dark");

            Path backup = database.backupTo(tempDir.resolve("backups"), NOW);

            assertTrue(Files.size(backup) > 0);
        }
    }

    @Test
    void aBackupNeverOverwritesAnExistingFile() throws SQLException, IOException {
        Database database = Database.openIn(tempDir.resolve("data"));
        Path folder = tempDir.resolve("backups");
        database.backupTo(folder, NOW);

        assertThrows(StorageException.class, () -> database.backupTo(folder, NOW));
    }
}
