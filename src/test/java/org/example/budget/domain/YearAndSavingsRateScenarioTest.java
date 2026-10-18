package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Facade-level scenarios for the Savings Rate and the year summary. */
class YearAndSavingsRateScenarioTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    private final InMemorySettingsRepository settings = new InMemorySettingsRepository();
    private final InMemoryTransactionRepository transactions = new InMemoryTransactionRepository();
    private final InMemoryCategoryRepository categories = new InMemoryCategoryRepository();
    private final InMemoryBudgetLimitRepository limits = new InMemoryBudgetLimitRepository();
    private final InMemoryRecurringTemplateRepository recurring = new InMemoryRecurringTemplateRepository();
    private final BudgetService service = new BudgetService(
            settings, categories, transactions, recurring, limits, Clock.fixed(TODAY));

    @BeforeEach
    void seed() {
        service.seedPresetCategories(PresetCategory::name);
    }

    @Test
    void theSavingsRateIsTheLeftoverShareOfTheIncome() {
        income(LocalDate.of(2026, 9, 10), "SALARY", 30_000);
        expense(LocalDate.of(2026, 9, 1), "RENT", 12_000);

        assertEquals(Optional.of(new BigDecimal("0.6000")), service.savingsRate(Month.of(2026, 9)));
    }

    @Test
    void fromSavingsIncomeIsLeftOutOfTheIncomeTheRateIsDividedBy() {
        income(LocalDate.of(2026, 9, 10), "SALARY", 30_000);
        income(LocalDate.of(2026, 9, 11), "FROM_SAVINGS", 10_000);
        expense(LocalDate.of(2026, 9, 1), "RENT", 20_000);

        // Leftover 20,000 ÷ Income 30,000 without From Savings (not ÷ 40,000)
        assertEquals(Optional.of(new BigDecimal("0.6667")), service.savingsRate(Month.of(2026, 9)));
    }

    @Test
    void theSavingsRateIsUndefinedWithoutIncomeOtherThanFromSavings() {
        assertEquals(Optional.empty(), service.savingsRate(Month.of(2026, 9)));

        income(LocalDate.of(2026, 9, 11), "FROM_SAVINGS", 10_000);
        expense(LocalDate.of(2026, 9, 1), "RENT", 2_000);

        assertEquals(Optional.empty(), service.savingsRate(Month.of(2026, 9)));
    }

    @Test
    void theSavingsRateCanBeNegativeWhenTheMonthSpentMoreThanItEarned() {
        income(LocalDate.of(2026, 9, 10), "SALARY", 30_000);
        expense(LocalDate.of(2026, 9, 1), "RENT", 33_000);

        assertEquals(Optional.of(new BigDecimal("-0.1000")), service.savingsRate(Month.of(2026, 9)));
    }

    @Test
    void theYearSummaryHasAllTwelveMonthsInOrderEvenWithoutTransactions() {
        YearSummary year = service.yearSummary(2026);

        assertEquals(12, year.months().size());
        assertEquals(Month.of(2026, 1), year.months().getFirst().month());
        assertEquals(Month.of(2026, 12), year.months().getLast().month());
        assertEquals(Money.ZERO, year.income());
        assertEquals(Money.ZERO, year.expenses());
        assertEquals(Money.ZERO, year.leftover());
        assertEquals(Optional.empty(), year.averageSavingsRate());
    }

    @Test
    void theYearTotalsAndTheAverageSavingsRateAddUpTheMonths() {
        seedYear();

        YearSummary year = service.yearSummary(2026);

        assertEquals(czk(94_000), year.income());
        assertEquals(czk(67_000), year.expenses());
        assertEquals(czk(27_000), year.leftover());
        // Months with a defined rate: Jan 0.6, Feb −0.1667, Apr 0.5. March has only From Savings Income.
        assertEquals(Optional.of(new BigDecimal("0.3111")), year.averageSavingsRate());
    }

    @Test
    void everyMonthShowsItsIncomeExpensesLeftoverAndSavingsRate() {
        seedYear();

        MonthOfYear january = service.yearSummary(2026).months().get(0);

        assertEquals(czk(30_000), january.income());
        assertEquals(czk(12_000), january.expenses());
        assertEquals(czk(18_000), january.leftover());
        assertEquals(Optional.of(new BigDecimal("0.6000")), january.savingsRate());
    }

    @Test
    void aCompletedMonthWithANegativeLeftoverIsMarkedAsAnUncoveredDeficit() {
        seedYear();

        List<MonthOfYear> months = service.yearSummary(2026).months();

        assertEquals(List.of(Month.of(2026, 2)),
                months.stream().filter(MonthOfYear::deficit).map(MonthOfYear::month).toList());
    }

    @Test
    void aCoveringIncomeClearsTheDeficitMarker() {
        seedYear();
        income(LocalDate.of(2026, 2, 20), "FROM_SAVINGS", 5_000);

        assertFalse(service.yearSummary(2026).months().get(1).deficit());
    }

    @Test
    void savingsFundedExpensesAreShownSeparatelyAndDoNotCountTowardTheLeftover() {
        seedYear();

        MonthOfYear april = service.yearSummary(2026).months().get(3);

        assertEquals(czk(25_000), april.savingsFunded());
        assertEquals(czk(20_000), april.expenses());
        assertEquals(czk(10_000), april.leftover());
        assertTrue(service.yearSummary(2026).months().get(0).savingsFunded().equals(Money.ZERO));
    }

    @Test
    void anotherYearIsNotAffected() {
        seedYear();

        YearSummary previous = service.yearSummary(2025);

        assertEquals(Money.ZERO, previous.income());
        assertEquals(Optional.empty(), previous.averageSavingsRate());
    }

    /** Jan saved, Feb a Deficit, Mar only From Savings Income, Apr a Savings-Funded PC. */
    private void seedYear() {
        income(LocalDate.of(2026, 1, 10), "SALARY", 30_000);
        expense(LocalDate.of(2026, 1, 1), "RENT", 12_000);
        income(LocalDate.of(2026, 2, 10), "SALARY", 30_000);
        expense(LocalDate.of(2026, 2, 1), "RENT", 35_000);
        income(LocalDate.of(2026, 3, 10), "FROM_SAVINGS", 4_000);
        income(LocalDate.of(2026, 4, 10), "SALARY", 20_000);
        income(LocalDate.of(2026, 4, 11), "FROM_SAVINGS", 10_000);
        expense(LocalDate.of(2026, 4, 1), "RENT", 20_000);
        service.recordTransaction(new TransactionDetails(LocalDate.of(2026, 4, 5), TransactionType.EXPENSE,
                id("ELECTRONICS"), czk(25_000), "PC").withSavingsFunded(true));
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
