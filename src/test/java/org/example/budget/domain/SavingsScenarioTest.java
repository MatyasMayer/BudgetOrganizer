package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Facade-level scenarios for Savings, Savings-Funded Expenses and From Savings Incomes. */
class SavingsScenarioTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    private final InMemorySettingsRepository settings = new InMemorySettingsRepository();
    private final InMemoryTransactionRepository transactions = new InMemoryTransactionRepository();
    private final InMemoryCategoryRepository categories = new InMemoryCategoryRepository();
    private final InMemoryBudgetLimitRepository limits = new InMemoryBudgetLimitRepository();
    private final InMemoryRecurringTemplateRepository recurring = new InMemoryRecurringTemplateRepository();
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

    @Test
    void aSavingsFundedPcComesOutOfSavingsNotOutOfTheMonthsLeftover() {
        service.setOpeningSavings(czk(50_000));
        long electronics = id("ELECTRONICS");
        service.setDefaultLimit(electronics, czk(2_000));
        income(LocalDate.of(2026, 10, 10), "SALARY", 30_000);
        expense(LocalDate.of(2026, 10, 1), "RENT", 12_000);
        savingsFunded(LocalDate.of(2026, 10, 12), "ELECTRONICS", 40_000);

        service = serviceOn(LocalDate.of(2026, 11, 1));
        MonthSummary october = service.monthSummary(Month.of(2026, 10));

        assertEquals(czk(18_000), october.leftover());
        assertEquals(czk(28_000), service.savings());
        MonthSummary.CategorySpending pc = spending(october, "ELECTRONICS");
        assertEquals(Money.ZERO, pc.total());
        assertEquals(czk(40_000), pc.fromSavings());
        assertEquals(Optional.of(BudgetBarStatus.GREEN), pc.barStatus());
        assertFalse(october.deficit());
    }

    @Test
    void aSavingsFundedExpenseReducesSavingsAlreadyInTheRunningMonth() {
        service.setOpeningSavings(czk(50_000));
        savingsFunded(LocalDate.of(2026, 10, 12), "ELECTRONICS", 40_000);

        assertEquals(czk(10_000), service.savings());
    }

    @Test
    void aSavingsFundedExpenseSharesTheCategorySliceWithOrdinarySpending() {
        expense(LocalDate.of(2026, 10, 3), "FOOD", 500);
        savingsFunded(LocalDate.of(2026, 10, 4), "FOOD", 3_000);

        MonthSummary.CategorySpending food = spending(service.monthSummary(Month.of(2026, 10)), "FOOD");

        assertEquals(czk(500), food.total());
        assertEquals(czk(3_000), food.fromSavings());
        assertEquals(czk(3_500), food.spent());
        assertEquals(czk(500), service.monthSummary(Month.of(2026, 10)).expenses());
    }

    @Test
    void aFromSavingsIncomeReducesSavings() {
        service.setOpeningSavings(czk(50_000));
        income(LocalDate.of(2026, 9, 10), "SALARY", 30_000);
        expense(LocalDate.of(2026, 9, 1), "RENT", 32_000);
        income(LocalDate.of(2026, 9, 30), "FROM_SAVINGS", 2_000);

        assertEquals(czk(48_000), service.savings());
    }

    @Test
    void editingAPastTransactionChangesSavings() {
        service.setOpeningSavings(czk(50_000));
        Transaction salary = income(LocalDate.of(2026, 9, 10), "SALARY", 30_000);
        Transaction tv = savingsFunded(LocalDate.of(2026, 9, 12), "ELECTRONICS", 10_000);

        service.editTransaction(salary.id(), new TransactionDetails(
                LocalDate.of(2026, 9, 10), TransactionType.INCOME, id("SALARY"), czk(25_000), ""));
        assertEquals(czk(65_000), service.savings());

        service.editTransaction(tv.id(), tv.details().withSavingsFunded(false));
        assertEquals(czk(65_000), service.savings());
    }

    @Test
    void savingsMayGoNegative() {
        service.setOpeningSavings(czk(1_000));
        savingsFunded(LocalDate.of(2026, 10, 12), "ELECTRONICS", 3_000);

        assertEquals(czk(-2_000), service.savings());
    }

    @Test
    void savingASavingsFundedExpenseThatMakesSavingsNegativeWarnsButStillSavesIt() {
        service.setOpeningSavings(czk(30_000));
        Transaction pc = savingsFunded(LocalDate.of(2026, 10, 12), "ELECTRONICS", 40_000);

        assertEquals(Optional.of(czk(-10_000)), service.negativeSavingsWarning(pc.details()));
        assertEquals(List.of(pc), service.transactions(TransactionFilter.ALL));
    }

    @Test
    void thereIsNoWarningWhileSavingsStayZeroOrMore() {
        service.setOpeningSavings(czk(40_000));
        Transaction pc = savingsFunded(LocalDate.of(2026, 10, 12), "ELECTRONICS", 40_000);

        assertEquals(Optional.empty(), service.negativeSavingsWarning(pc.details()));
    }

    @Test
    void onlySavingsFundedExpensesWarnAboutNegativeSavings() {
        service.setOpeningSavings(czk(-5_000));
        Transaction food = expense(LocalDate.of(2026, 10, 12), "FOOD", 100);

        assertEquals(Optional.empty(), service.negativeSavingsWarning(food.details()));
    }

    @Test
    void anIncomeCannotBeSavingsFunded() {
        TransactionDetails gift = new TransactionDetails(LocalDate.of(2026, 10, 12), TransactionType.INCOME,
                id("GIFT"), czk(100), "").withSavingsFunded(true);

        assertEquals(InvalidTransactionException.Problem.SAVINGS_FUNDED_INCOME,
                assertThrows(InvalidTransactionException.class, () -> service.recordTransaction(gift)).problem());
    }

    @Test
    void theMonthComparisonLeavesSavingsFundedExpensesOut() {
        expense(LocalDate.of(2026, 9, 3), "FOOD", 500);
        savingsFunded(LocalDate.of(2026, 9, 4), "FOOD", 3_000);
        expense(LocalDate.of(2026, 10, 3), "FOOD", 700);
        savingsFunded(LocalDate.of(2026, 10, 4), "FOOD", 9_000);

        Comparison fullMonth = service.monthComparison(Month.of(2026, 10)).fullMonth();

        assertEquals(new Change(czk(700), czk(500)), fullMonth.categories().get(0).change());
    }

    private Transaction savingsFunded(LocalDate date, String category, long czk) {
        return service.recordTransaction(new TransactionDetails(
                date, TransactionType.EXPENSE, id(category), czk(czk), "").withSavingsFunded(true));
    }

    private static MonthSummary.CategorySpending spending(MonthSummary summary, String category) {
        return summary.expensesByCategory().stream()
                .filter(spending -> spending.category().name().equals(category))
                .findFirst().orElseThrow();
    }

    private BudgetService serviceOn(LocalDate today) {
        return new BudgetService(settings, categories, transactions, recurring, limits, Clock.fixed(today));
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
