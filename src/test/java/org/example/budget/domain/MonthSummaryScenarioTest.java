package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Facade-level scenarios for the Month summary shown on the Month dashboard. */
class MonthSummaryScenarioTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
    private static final Month OCTOBER = Month.of(2026, 10);

    private final BudgetService service = new BudgetService(
            new InMemorySettingsRepository(), new InMemoryCategoryRepository(), new InMemoryTransactionRepository(),
            Clock.fixed(TODAY));

    @BeforeEach
    void seed() {
        service.seedPresetCategories(PresetCategory::name);
    }

    @Test
    void salary30000AndRent12000LeaveALeftoverOf18000() {
        income(LocalDate.of(2026, 10, 10), "SALARY", 30_000);
        expense(LocalDate.of(2026, 10, 1), "RENT", 12_000);

        MonthSummary summary = service.monthSummary(OCTOBER);

        assertEquals(OCTOBER, summary.month());
        assertEquals(czk(30_000), summary.income());
        assertEquals(czk(12_000), summary.expenses());
        assertEquals(czk(18_000), summary.leftover());
    }

    @Test
    void expensesAreSplitByCategoryLargestFirstAndARefundReducesItsCategory() {
        expense(LocalDate.of(2026, 10, 1), "RENT", 12_000);
        expense(LocalDate.of(2026, 10, 3), "FOOD", 2_500);
        expense(LocalDate.of(2026, 10, 5), "ELECTRONICS", 8_000);
        expense(LocalDate.of(2026, 10, 12), "ELECTRONICS", -3_000);
        expense(LocalDate.of(2026, 10, 14), "FOOD", 700);

        MonthSummary summary = service.monthSummary(OCTOBER);

        assertEquals(List.of(
                        spending("RENT", 12_000), spending("ELECTRONICS", 5_000), spending("FOOD", 3_200)),
                summary.expensesByCategory());
        assertEquals(czk(20_200), summary.expenses());
    }

    @Test
    void transactionsOfOtherMonthsAreLeftOut() {
        income(LocalDate.of(2026, 9, 30), "SALARY", 30_000);
        expense(LocalDate.of(2026, 9, 30), "FOOD", 400);
        expense(LocalDate.of(2026, 10, 31), "FOOD", 100);
        income(LocalDate.of(2026, 11, 1), "GIFT", 1_000);
        expense(LocalDate.of(2025, 10, 15), "GAMES", 999);

        MonthSummary summary = service.monthSummary(OCTOBER);

        assertEquals(Money.ZERO, summary.income());
        assertEquals(czk(100), summary.expenses());
        assertEquals(czk(-100), summary.leftover());
        assertEquals(List.of(spending("FOOD", 100)), summary.expensesByCategory());
    }

    @Test
    void anEmptyMonthHasZeroTotalsAndNoCategories() {
        MonthSummary summary = service.monthSummary(Month.of(2026, 2));

        assertEquals(Money.ZERO, summary.income());
        assertEquals(Money.ZERO, summary.expenses());
        assertEquals(Money.ZERO, summary.leftover());
        assertEquals(List.of(), summary.expensesByCategory());
    }

    @Test
    void incomesAreNotPartOfTheExpenseSplit() {
        income(LocalDate.of(2026, 10, 10), "SALARY", 30_000);
        income(LocalDate.of(2026, 10, 20), "GIFT", 500);
        expense(LocalDate.of(2026, 10, 2), "GAMES", 1_200);

        MonthSummary summary = service.monthSummary(OCTOBER);

        assertEquals(czk(30_500), summary.income());
        assertEquals(List.of(spending("GAMES", 1_200)), summary.expensesByCategory());
        assertEquals(czk(29_300), summary.leftover());
    }

    @Test
    void editingAndDeletingATransactionChangesTheSummary() {
        Transaction lunch = service.recordTransaction(new TransactionDetails(
                LocalDate.of(2026, 10, 3), TransactionType.EXPENSE, id("FOOD"), czk(300), "Lunch"));
        Transaction game = service.recordTransaction(new TransactionDetails(
                LocalDate.of(2026, 10, 4), TransactionType.EXPENSE, id("GAMES"), czk(900), "Steam"));

        service.editTransaction(lunch.id(), new TransactionDetails(
                LocalDate.of(2026, 10, 3), TransactionType.EXPENSE, id("GOING_OUT"), czk(450), "Dinner"));
        service.deleteTransaction(game.id());

        MonthSummary summary = service.monthSummary(OCTOBER);
        assertEquals(List.of(spending("GOING_OUT", 450)), summary.expensesByCategory());
        assertEquals(czk(450), summary.expenses());
    }

    private MonthSummary.CategorySpending spending(String category, long czk) {
        Category found = service.categories(TransactionType.EXPENSE).stream()
                .filter(c -> c.name().equals(category)).findFirst().orElseThrow();
        return new MonthSummary.CategorySpending(found, czk(czk));
    }

    private void income(LocalDate date, String category, long czk) {
        service.recordTransaction(new TransactionDetails(date, TransactionType.INCOME, id(category), czk(czk), ""));
    }

    private void expense(LocalDate date, String category, long czk) {
        service.recordTransaction(new TransactionDetails(date, TransactionType.EXPENSE, id(category), czk(czk), ""));
    }

    private long id(String name) {
        return java.util.stream.Stream.of(TransactionType.values())
                .flatMap(type -> service.categories(type).stream())
                .filter(category -> category.name().equals(name))
                .findFirst().orElseThrow().id();
    }

    private static Money czk(long czk) {
        return Money.ofHalere(czk * 100);
    }
}
