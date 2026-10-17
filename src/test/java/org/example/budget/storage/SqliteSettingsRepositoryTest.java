package org.example.budget.storage;

import org.example.budget.domain.SettingsRepository;
import org.example.budget.domain.SettingsRepositoryContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteSettingsRepositoryTest extends SettingsRepositoryContract {

    @TempDir
    Path tempDir;

    @Override
    protected SettingsRepository createRepository() throws SQLException {
        return new SqliteSettingsRepository(Database.openIn(tempDir));
    }

    @Test
    void settingsSurviveReopeningTheDatabase() throws SQLException {
        new SqliteSettingsRepository(Database.openIn(tempDir)).save("language", "cs");

        SettingsRepository reopened = new SqliteSettingsRepository(Database.openIn(tempDir));

        assertEquals(Optional.of("cs"), reopened.find("language"));
    }
}
