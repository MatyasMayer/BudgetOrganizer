package org.example.budget.storage;

import org.example.budget.domain.TransactionRepositoryContract;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;

class SqliteTransactionRepositoryTest extends TransactionRepositoryContract {

    @TempDir
    Path tempDir;

    @Override
    protected Repositories createRepositories() throws SQLException {
        Database database = Database.openIn(tempDir);
        return new Repositories(new SqliteCategoryRepository(database), new SqliteTransactionRepository(database));
    }
}
