package org.example.budget.storage;

import org.example.budget.domain.SettingsRepository;
import org.example.budget.domain.SettingsRepositoryContract;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;

class SqliteSettingsRepositoryTest extends SettingsRepositoryContract {

    @TempDir
    Path tempDir;

    @Override
    protected SettingsRepository createRepository() throws SQLException {
        return new SqliteSettingsRepository(Database.openIn(tempDir));
    }
}
