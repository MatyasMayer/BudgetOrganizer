package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Facade-level scenarios for the Top spending screen: ranked Categories and the biggest single Expenses. */
class TopSpendingScenarioTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
    private static final Month OCTOBER = Month.of(2026, 10);

    private final BudgetService service = new BudgetService(
            new InMemorySettingsRepository(), new InMemoryCategoryRepository(), new InMemoryTransactionRepository(),
            new InMemoryRecurringTemplateRepository(), new InMemoryBudgetLimitRepository(), Clock.fixed(TODAY));

    @BeforeEach
    void seed() {
        service.seedPresetCategories(PresetCategory::name);
    }

    @Test
    void expenseCategoriesAreRankedByTheirTotalForTheMonth() {
        expense(LocalDate.of(2026, 10, 2), "FOOD", 3_000);
        expense(LocalDate.of(2026, 10, 9), "FOOD", 2_000);
        expense(LocalDate.of(2026, 10, 1), "RENT", 12_000);
        expense(LocalDate.of(2026, 10, 5), "TRANSPORT", 1_000);
        expense(LocalDate.of(2026, 9, 5), "GAMES", 99_000);
        income(LocalDate.of(2026, 10, 10), "SALARY", 50_000);

        TopSpending top = service.topSpending(OCTOBER);

        assertEquals(List.of("RENT", "FOOD", "TRANSPORT"),
                top.categories().stream().map(rank -> rank.category().name()).toList());
        assertEquals(List.of(czk(12_000), czk(5_000), czk(1_000)),
                top.categories().stream().map(CategoryRank::total).toList());
    }

    @Test
    void aRefundLowersItsCategoryAndIsNeverListedAsABiggestExpense() {
        expense(LocalDate.of(2026, 10, 2), "FOOD", 3_000);
        expense(LocalDate.of(2026, 10, 3), "FOOD", -500);
        expense(LocalDate.of(2026, 10, 4), "TRANSPORT", 2_600);

        TopSpending top = service.topSpending(OCTOBER);

        assertEquals(List.of("TRANSPORT", "FOOD"),
                top.categories().stream().map(rank -> rank.category().name()).toList());
        assertEquals(czk(2_500), top.categories().get(1).total());
        assertEquals(List.of(czk(3_000), czk(2_600)),
                top.biggestExpenses().stream().map(Transaction::amount).toList());
    }

    @Test
    void aCategoryWhoseRefundsCancelItsSpendingIsNotRanked() {
        expense(LocalDate.of(2026, 10, 2), "CLOTHES", 1_000);
        expense(LocalDate.of(2026, 10, 3), "CLOTHES", -1_000);

        assertEquals(List.of(), service.topSpending(OCTOBER).categories());
    }

    @Test
    void theFiveBiggestSingleExpensesAreListedLargestFirst() {
        expense(LocalDate.of(2026, 10, 1), "RENT", 12_000);
        expense(LocalDate.of(2026, 10, 2), "FOOD", 900);
        expense(LocalDate.of(2026, 10, 3), "FOOD", 800);
        expense(LocalDate.of(2026, 10, 4), "TRANSPORT", 1_500);
        expense(LocalDate.of(2026, 10, 5), "HEALTH", 700);
        expense(LocalDate.of(2026, 10, 6), "GIFTS", 600);
        expense(LocalDate.of(2026, 10, 7), "CLOTHES", 3_000);
        income(LocalDate.of(2026, 10, 8), "SALARY", 80_000);

        List<Transaction> biggest = service.topSpending(OCTOBER).biggestExpenses();

        assertEquals(List.of(czk(12_000), czk(3_000), czk(1_500), czk(900), czk(800)),
                biggest.stream().map(Transaction::amount).toList());
        assertEquals(LocalDate.of(2026, 10, 7), biggest.get(1).date());
    }

    @Test
    void savingsFundedExpensesAreIncludedAndMarkedFromSavings() {
        expense(LocalDate.of(2026, 10, 2), "ELECTRONICS", 1_000);
        service.recordTransaction(new TransactionDetails(LocalDate.of(2026, 10, 5), TransactionType.EXPENSE,
                id("ELECTRONICS"), czk(25_000), "PC").withSavingsFunded(true));
        expense(LocalDate.of(2026, 10, 6), "FOOD", 4_000);

        TopSpending top = service.topSpending(OCTOBER);

        CategoryRank electronics = top.categories().getFirst();
        assertEquals("ELECTRONICS", electronics.category().name());
        assertEquals(czk(26_000), electronics.total());
        assertEquals(czk(25_000), electronics.fromSavings());
        assertTrue(electronics.hasFromSavings());
        Transaction pc = top.biggestExpenses().getFirst();
        assertEquals(czk(25_000), pc.amount());
        assertTrue(pc.details().savingsFunded());
    }

    @Test
    void aYearRanksAndListsEverythingFromItsTwelveMonths() {
        expense(LocalDate.of(2026, 1, 1), "RENT", 12_000);
        expense(LocalDate.of(2026, 10, 1), "RENT", 12_000);
        expense(LocalDate.of(2026, 3, 8), "FOOD", 9_000);
        expense(LocalDate.of(2026, 12, 31), "FOOD", 8_000);
        expense(LocalDate.of(2025, 12, 31), "GAMES", 90_000);
        expense(LocalDate.of(2027, 1, 1), "GAMES", 90_000);

        TopSpending top = service.topSpending(2026);

        assertEquals(List.of("RENT", "FOOD"),
                top.categories().stream().map(rank -> rank.category().name()).toList());
        assertEquals(List.of(czk(24_000), czk(17_000)),
                top.categories().stream().map(CategoryRank::total).toList());
        assertEquals(List.of(czk(12_000), czk(12_000), czk(9_000), czk(8_000)),
                top.biggestExpenses().stream().map(Transaction::amount).toList());
    }

    @Test
    void aPeriodWithoutExpensesHasNothingToRank() {
        income(LocalDate.of(2026, 10, 8), "SALARY", 80_000);

        TopSpending top = service.topSpending(OCTOBER);

        assertEquals(List.of(), top.categories());
        assertEquals(List.of(), top.biggestExpenses());
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
