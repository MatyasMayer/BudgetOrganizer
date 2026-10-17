package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Facade-level scenarios for Savings, Savings-Funded Expenses and From Savings Incomes. */
class SavingsScenarioTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    private final InMemorySettingsRepository settings = new InMemorySettingsRepository();
    private final InMemoryTransactionRepository transactions = new InMemoryTransactionRepository();
    private final InMemoryCategoryRepository categories = new InMemoryCategoryRepository();
    private final InMemoryBudgetLimitRepository limits = new InMemoryBudgetLimitRepository();
    private BudgetService service = serviceOn(TODAY);

    @BeforeEach
    void seed() {
        service.seedPresetCategories(PresetCategory::name);
    }

    @Test
    void withoutTransactionsSavingsAreTheOpeningSavings() {
        service.setOpeningSavings(czk(50_000));

        assertEquals(czk(50_000), service.savings());
    }

    @Test
    void thePositiveLeftoverOfACompletedMonthGoesToSavings() {
        service.setOpeningSavings(czk(50_000));
        income(LocalDate.of(2026, 9, 10), "SALARY", 30_000);
        expense(LocalDate.of(2026, 9, 1), "RENT", 12_000);

        assertEquals(czk(68_000), service.savings());
    }

    @Test
    void aMonthWithANegativeLeftoverDoesNotReduceSavings() {
        service.setOpeningSavings(czk(50_000));
        income(LocalDate.of(2026, 8, 10), "SALARY", 30_000);
        expense(LocalDate.of(2026, 8, 1), "RENT", 32_000);
        income(LocalDate.of(2026, 9, 10), "SALARY", 30_000);
        expense(LocalDate.of(2026, 9, 1), "RENT", 12_000);

        assertEquals(czk(68_000), service.savings());
    }

    @Test
    void theRunningMonthAddsToSavingsOnlyOnceItIsCompleted() {
        service.setOpeningSavings(czk(50_000));
        income(LocalDate.of(2026, 10, 10), "SALARY", 30_000);
        expense(LocalDate.of(2026, 10, 1), "RENT", 12_000);
        assertEquals(czk(50_000), service.savings());

        service = serviceOn(LocalDate.of(2026, 11, 1));

        assertEquals(czk(68_000), service.savings());
    }

    @Test
    void aFutureMonthDoesNotAddToSavings() {
        income(LocalDate.of(2026, 12, 10), "SALARY", 30_000);

        assertEquals(Money.ZERO, service.savings());
    }

    private BudgetService serviceOn(LocalDate today) {
        return new BudgetService(settings, categories, transactions, limits, Clock.fixed(today));
    }

    private Transaction income(LocalDate date, String category, long czk) {
        return service.recordTransaction(new TransactionDetails(date, TransactionType.INCOME, id(category), czk(czk), ""));
    }

    private Transaction expense(LocalDate date, String category, long czk) {
        return service.recordTransaction(new TransactionDetails(date, TransactionType.EXPENSE, id(category), czk(czk), ""));
    }

    private long id(String name) {
        return Stream.of(TransactionType.values())
                .flatMap(type -> service.categories(type).stream())
                .filter(category -> category.name().equals(name))
                .findFirst().orElseThrow().id();
    }

    private static Money czk(long czk) {
        return Money.ofHalere(czk * 100);
    }
}
