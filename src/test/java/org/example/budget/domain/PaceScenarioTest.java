package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Facade-level scenarios for the running Month's Pace, daily average and safe-to-spend. */
class PaceScenarioTest {

    private static final LocalDate DAY_2 = LocalDate.of(2026, 10, 2);

    private final InMemorySettingsRepository settings = new InMemorySettingsRepository();
    private final InMemoryTransactionRepository transactions = new InMemoryTransactionRepository();
    private final InMemoryCategoryRepository categories = new InMemoryCategoryRepository();
    private final InMemoryBudgetLimitRepository limits = new InMemoryBudgetLimitRepository();
    private final InMemoryRecurringTemplateRepository recurring = new InMemoryRecurringTemplateRepository();
    private BudgetService service = serviceOn(DAY_2);

    @BeforeEach
    void seed() {
        service.seedPresetCategories(PresetCategory::name);
    }

    @Test
    void rentGeneratedOnTheFirstDoesNotBlowUpThePaceOnDayTwo() {
        monthlyTemplate("RENT", 12_000, 1);
        service.generateDueOccurrences();
        expense(DAY_2, "FOOD", 500);

        MonthPace pace = service.monthPace();

        // 500 over 2 days of 31 → 7,750 of Variable Spending, plus the 12,000 Rent as a known amount
        assertEquals(czk(19_750), pace.pace());
        assertEquals(czk(250), pace.dailyAverage());
    }

    @Test
    void occurrencesStillDueLaterInTheMonthCountAsFixedSpending() {
        monthlyTemplate("RENT", 12_000, 1);
        monthlyTemplate("SUBSCRIPTIONS", 300, 20);
        service.generateDueOccurrences();
        expense(DAY_2, "FOOD", 500);

        assertEquals(czk(20_050), service.monthPace().pace());
    }

    @Test
    void anOccurrenceOfAnEndedOrFutureTemplateIsNotFixedSpending() {
        service.createRecurringTemplate(new RecurringTemplateDetails(TransactionType.EXPENSE, id("HEALTH"), czk(900),
                "Gym", Frequency.MONTHLY, 20, LocalDate.of(2026, 1, 1), Optional.of(LocalDate.of(2026, 9, 30))));
        service.createRecurringTemplate(new RecurringTemplateDetails(TransactionType.EXPENSE, id("SUBSCRIPTIONS"),
                czk(300), "Later", Frequency.MONTHLY, 20, LocalDate.of(2026, 11, 1), Optional.empty()));
        service.createRecurringTemplate(new RecurringTemplateDetails(TransactionType.INCOME, id("SALARY"),
                czk(30_000), "Salary", Frequency.MONTHLY, 20, LocalDate.of(2026, 1, 1), Optional.empty()));

        assertEquals(Money.ZERO, service.monthPace().pace());
    }

    @Test
    void aLimitedCategoryShowsItsOwnPaceAndAWarningWhenItExceedsTheLimit() {
        service.setDefaultLimit(id("FOOD"), czk(6_000));
        service.setDefaultLimit(id("GOING_OUT"), czk(1_000));
        expense(DAY_2, "FOOD", 500);
        expense(DAY_2, "GOING_OUT", 20);

        MonthPace pace = service.monthPace();

        assertEquals(2, pace.categories().size());
        CategoryPace food = categoryPace(pace, "FOOD");
        assertEquals(czk(7_750), food.pace());
        assertEquals(czk(6_000), food.limit());
        assertTrue(food.exceedsLimit());
        CategoryPace goingOut = categoryPace(pace, "GOING_OUT");
        assertEquals(czk(310), goingOut.pace());
        assertFalse(goingOut.exceedsLimit());
    }

    @Test
    void aCategoryWithoutALimitHasNoPaceOfItsOwn() {
        expense(DAY_2, "FOOD", 500);

        assertEquals(List.of(), service.monthPace().categories());
    }

    @Test
    void aLimitedCategoryAddsItsFixedSpendingToItsPaceAndTakesItOffItsSafeToSpend() {
        service.setDefaultLimit(id("RENT"), czk(12_500));
        monthlyTemplate("RENT", 12_000, 1);
        service.generateDueOccurrences();

        CategoryPace rent = categoryPace(service.monthPace(), "RENT");

        assertEquals(czk(12_000), rent.pace());
        assertFalse(rent.exceedsLimit());
        // (12,500 − 12,000 − 0) ÷ 30 remaining days, rounded down to the haléř
        assertEquals(Money.ofHalere(1_666), rent.safeToSpendPerDay());
    }

    @Test
    void safeToSpendKeepsTheMonthOutOfADeficit() {
        income(LocalDate.of(2026, 10, 1), "SALARY", 30_000);
        monthlyTemplate("RENT", 12_000, 1);
        monthlyTemplate("SUBSCRIPTIONS", 300, 20);
        service.generateDueOccurrences();
        expense(DAY_2, "FOOD", 500);

        // (30,000 − 12,300 fixed − 500 so far) ÷ 30 remaining days = 573.33
        assertEquals(Money.ofHalere(57_333), service.monthPace().safeToSpendPerDay());
    }

    @Test
    void safeToSpendNeverGoesBelowZero() {
        income(LocalDate.of(2026, 10, 1), "SALARY", 300);
        service.setDefaultLimit(id("FOOD"), czk(100));
        expense(DAY_2, "FOOD", 500);

        MonthPace pace = service.monthPace();

        assertEquals(Money.ZERO, pace.safeToSpendPerDay());
        assertEquals(Money.ZERO, categoryPace(pace, "FOOD").safeToSpendPerDay());
    }

    @Test
    void theLastDayOfTheMonthStillHasOneRemainingDay() {
        service = serviceOn(LocalDate.of(2026, 10, 31));
        income(LocalDate.of(2026, 10, 1), "SALARY", 1_000);

        assertEquals(czk(1_000), service.monthPace().safeToSpendPerDay());
    }

    @Test
    void savingsFundedExpensesAreLeftOutOfEverything() {
        income(LocalDate.of(2026, 10, 1), "SALARY", 30_000);
        service.setDefaultLimit(id("ELECTRONICS"), czk(1_000));
        service.recordTransaction(new TransactionDetails(DAY_2, TransactionType.EXPENSE, id("ELECTRONICS"),
                czk(25_000), "PC").withSavingsFunded(true));

        MonthPace pace = service.monthPace();

        assertEquals(Money.ZERO, pace.pace());
        assertEquals(Money.ZERO, pace.dailyAverage());
        assertEquals(Money.ofHalere(100_000), pace.safeToSpendPerDay());
        assertEquals(Money.ZERO, categoryPace(pace, "ELECTRONICS").pace());
    }

    @Test
    void aGeneratedExpenseIsNotCountedAsVariableSpending() {
        monthlyTemplate("RENT", 12_000, 1);
        service.generateDueOccurrences();

        assertEquals(Money.ZERO, service.monthPace().dailyAverage());
    }

    private CategoryPace categoryPace(MonthPace pace, String category) {
        return pace.categories().stream().filter(c -> c.category().id() == id(category)).findFirst().orElseThrow();
    }

    private void monthlyTemplate(String category, long czk, int day) {
        service.createRecurringTemplate(new RecurringTemplateDetails(TransactionType.EXPENSE, id(category), czk(czk),
                category, Frequency.MONTHLY, day, LocalDate.of(2026, 9, 1), Optional.empty()));
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
