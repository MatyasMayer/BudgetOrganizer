package org.example.budget.storage;

import org.example.budget.domain.BudgetService;
import org.example.budget.domain.Category;
import org.example.budget.domain.Clock;
import org.example.budget.domain.Money;
import org.example.budget.domain.Month;
import org.example.budget.domain.TransactionDetails;
import org.example.budget.domain.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Deleting Categories that have Budget Limits works on SQLite, where every reference is enforced. */
class SqliteCategoryDeletionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
    private static final Month DECEMBER = Month.of(2026, 12);

    @TempDir
    Path tempDir;

    private BudgetService service;

    @BeforeEach
    void setUp() throws SQLException {
        Database database = Database.openIn(tempDir);
        service = new BudgetService(new SqliteSettingsRepository(database), new SqliteCategoryRepository(database),
                new SqliteTransactionRepository(database), new SqliteRecurringTemplateRepository(database),
                new SqliteBudgetLimitRepository(database),
                Clock.fixed(TODAY));
    }

    @Test
    void aCategoryWithBudgetLimitsAndNoTransactionsCanBeDeleted() {
        Category pets = service.addCategory("Pets", TransactionType.EXPENSE);
        service.setDefaultLimit(pets.id(), Money.ofHalere(80_000));
        service.setLimitOverride(pets.id(), DECEMBER, Money.ofHalere(150_000));

        service.deleteCategory(pets.id());

        assertEquals(Optional.empty(), service.effectiveLimit(pets.id(), DECEMBER));
    }

    @Test
    void mergingMovesBudgetLimitsAndDropsTheOnesTheReplacementAlreadyHas() {
        Category food = service.addCategory("Food", TransactionType.EXPENSE);
        Category other = service.addCategory("Other", TransactionType.EXPENSE);
        service.setDefaultLimit(food.id(), Money.ofHalere(600_000));
        service.setLimitOverride(food.id(), DECEMBER, Money.ofHalere(900_000));
        service.setDefaultLimit(other.id(), Money.ofHalere(200_000));
        service.recordTransaction(new TransactionDetails(
                TODAY, TransactionType.EXPENSE, food.id(), Money.ofHalere(10_000), "Lunch"));

        service.deleteCategory(food.id(), other.id());

        assertEquals(Optional.of(Money.ofHalere(200_000)), service.defaultLimit(other.id()));
        assertEquals(Optional.of(Money.ofHalere(900_000)), service.limitOverride(other.id(), DECEMBER));
    }
}
