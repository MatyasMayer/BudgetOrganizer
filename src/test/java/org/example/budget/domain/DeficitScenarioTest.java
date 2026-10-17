package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Facade-level scenarios for Deficits, Uncovered Deficits and Covering Income. */
class DeficitScenarioTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
    private static final Month SEPTEMBER = Month.of(2026, 9);
    private static final Month OCTOBER = Month.of(2026, 10);

    private final BudgetService service = new BudgetService(
            new InMemorySettingsRepository(), new InMemoryCategoryRepository(), new InMemoryTransactionRepository(),
            new InMemoryRecurringTemplateRepository(), Clock.fixed(TODAY));

    @BeforeEach
    void seed() {
        service.seedPresetCategories(PresetCategory::name);
    }

    @Test
    void aCompletedMonthWithANegativeLeftoverIsADeficit() {
        income(LocalDate.of(2026, 9, 10), "SALARY", 30_000);
        expense(LocalDate.of(2026, 9, 1), "RENT", 32_000);

        assertTrue(service.monthSummary(SEPTEMBER).deficit());
    }

    @Test
    void aCompletedMonthWithALeftoverOfZeroOrMoreIsNoDeficit() {
        income(LocalDate.of(2026, 9, 10), "SALARY", 30_000);
        expense(LocalDate.of(2026, 9, 1), "RENT", 30_000);

        assertFalse(service.monthSummary(SEPTEMBER).deficit());
    }

    @Test
    void theRunningMonthIsNeverADeficitEvenWithANegativeLeftover() {
        expense(LocalDate.of(2026, 10, 1), "RENT", 12_000);

        assertFalse(service.monthSummary(OCTOBER).deficit());
    }

    @Test
    void aFutureMonthIsNoDeficit() {
        expense(LocalDate.of(2026, 11, 1), "RENT", 12_000);

        assertFalse(service.monthSummary(Month.of(2026, 11)).deficit());
    }

    @Test
    void aCoveringIncomeThatOnlyPartlyCoversTheDeficitLeavesTheMonthFlagged() {
        income(LocalDate.of(2026, 9, 10), "SALARY", 30_000);
        expense(LocalDate.of(2026, 9, 1), "RENT", 32_000);

        income(LocalDate.of(2026, 9, 30), "FROM_SAVINGS", 1_500);

        assertTrue(service.monthSummary(SEPTEMBER).deficit());
    }

    @Test
    void aCoveringIncomeThatBringsTheLeftoverToZeroClearsTheDeficit() {
        income(LocalDate.of(2026, 9, 10), "SALARY", 30_000);
        expense(LocalDate.of(2026, 9, 1), "RENT", 32_000);

        income(LocalDate.of(2026, 9, 30), "FROM_SAVINGS", 1_500);
        income(LocalDate.of(2026, 9, 21), "GIFT", 500);

        assertFalse(service.monthSummary(SEPTEMBER).deficit());
    }

    @Test
    void editingTheCoveringIncomeDownReopensTheDeficit() {
        income(LocalDate.of(2026, 9, 10), "SALARY", 30_000);
        expense(LocalDate.of(2026, 9, 1), "RENT", 32_000);
        Transaction gift = income(LocalDate.of(2026, 9, 21), "GIFT", 2_000);

        service.editTransaction(gift.id(), new TransactionDetails(
                LocalDate.of(2026, 9, 21), TransactionType.INCOME, id("GIFT"), czk(1_999), ""));

        assertTrue(service.monthSummary(SEPTEMBER).deficit());
    }

    @Test
    void movingTheCoveringIncomeToAnotherMonthOrAddingAnExpenseReopensTheDeficit() {
        income(LocalDate.of(2026, 9, 10), "SALARY", 30_000);
        expense(LocalDate.of(2026, 9, 1), "RENT", 32_000);
        Transaction gift = income(LocalDate.of(2026, 9, 21), "GIFT", 2_000);

        service.editTransaction(gift.id(), new TransactionDetails(
                LocalDate.of(2026, 8, 21), TransactionType.INCOME, id("GIFT"), czk(2_000), ""));
        assertTrue(service.monthSummary(SEPTEMBER).deficit());

        income(LocalDate.of(2026, 9, 25), "OTHER_INCOME", 2_000);
        assertFalse(service.monthSummary(SEPTEMBER).deficit());

        expense(LocalDate.of(2026, 9, 28), "FOOD", 1);
        assertTrue(service.monthSummary(SEPTEMBER).deficit());
    }

    @Test
    void deletingTheCoveringIncomeReopensTheDeficit() {
        income(LocalDate.of(2026, 9, 10), "SALARY", 30_000);
        expense(LocalDate.of(2026, 9, 1), "RENT", 32_000);
        Transaction fromSavings = income(LocalDate.of(2026, 9, 30), "FROM_SAVINGS", 2_000);

        service.deleteTransaction(fromSavings.id());

        assertTrue(service.monthSummary(SEPTEMBER).deficit());
    }

    @Test
    void theDashboardListsEveryUncoveredDeficitOldestFirstWithItsLeftover() {
        expense(LocalDate.of(2026, 9, 1), "RENT", 12_000);
        expense(LocalDate.of(2025, 12, 24), "GIFTS", 3_000);
        income(LocalDate.of(2025, 12, 10), "SALARY", 2_500);
        expense(LocalDate.of(2026, 3, 5), "FOOD", 400);
        income(LocalDate.of(2026, 3, 30), "FROM_SAVINGS", 400);
        income(LocalDate.of(2026, 5, 10), "SALARY", 30_000);
        expense(LocalDate.of(2026, 5, 1), "RENT", 12_000);
        expense(LocalDate.of(2026, 10, 1), "RENT", 12_000);
        expense(LocalDate.of(2026, 11, 1), "RENT", 12_000);

        assertEquals(List.of(
                        new Deficit(Month.of(2025, 12), czk(-500)),
                        new Deficit(SEPTEMBER, czk(-12_000))),
                service.uncoveredDeficits());
    }

    @Test
    void aFullyCoveredDeficitLeavesTheList() {
        expense(LocalDate.of(2026, 8, 3), "FOOD", 1_000);
        Transaction partial = income(LocalDate.of(2026, 8, 30), "GIFT", 600);
        assertEquals(List.of(new Deficit(Month.of(2026, 8), czk(-400))), service.uncoveredDeficits());

        service.editTransaction(partial.id(), new TransactionDetails(
                LocalDate.of(2026, 8, 30), TransactionType.INCOME, id("GIFT"), czk(1_000), ""));

        assertEquals(List.of(), service.uncoveredDeficits());
    }

    @Test
    void withoutTransactionsThereAreNoUncoveredDeficits() {
        assertEquals(List.of(), service.uncoveredDeficits());
    }

    private Transaction income(LocalDate date, String category, long czk) {
        return service.recordTransaction(new TransactionDetails(date, TransactionType.INCOME, id(category), czk(czk), ""));
    }

    private Transaction expense(LocalDate date, String category, long czk) {
        return service.recordTransaction(new TransactionDetails(date, TransactionType.EXPENSE, id(category), czk(czk), ""));
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
