package org.example.budget.storage;

import org.example.budget.domain.RecurringTemplateRepositoryContract;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;

class SqliteRecurringTemplateRepositoryTest extends RecurringTemplateRepositoryContract {

    @TempDir
    Path tempDir;

    @Override
    protected Repositories createRepositories() throws SQLException {
        Database database = Database.openIn(tempDir);
        return new Repositories(new SqliteCategoryRepository(database), new SqliteTransactionRepository(database),
                new SqliteRecurringTemplateRepository(database));
    }
}
