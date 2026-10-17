package org.example.budget.storage;

import org.example.budget.domain.CategoryRepository;
import org.example.budget.domain.CategoryRepositoryContract;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;

class SqliteCategoryRepositoryTest extends CategoryRepositoryContract {

    @TempDir
    Path tempDir;

    @Override
    protected CategoryRepository createRepository() throws SQLException {
        return new SqliteCategoryRepository(Database.openIn(tempDir));
    }
}
