package org.example.budget.storage;

import org.example.budget.domain.BudgetLimitRepositoryContract;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;

class SqliteBudgetLimitRepositoryTest extends BudgetLimitRepositoryContract {

    @TempDir
    Path tempDir;

    @Override
    protected Repositories createRepositories() throws SQLException {
        Database database = Database.openIn(tempDir);
        return new Repositories(new SqliteCategoryRepository(database), new SqliteBudgetLimitRepository(database));
    }
}
