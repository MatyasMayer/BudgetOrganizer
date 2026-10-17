package org.example.budget.storage;

import org.example.budget.domain.CnbRateRepository;
import org.example.budget.domain.CnbRateRepositoryContract;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;

class SqliteCnbRateRepositoryTest extends CnbRateRepositoryContract {

    @TempDir
    Path tempDir;

    @Override
    protected CnbRateRepository createRepository() throws SQLException {
        return new SqliteCnbRateRepository(Database.openIn(tempDir));
    }
}
