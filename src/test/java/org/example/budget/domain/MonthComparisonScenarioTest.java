package org.example.budget.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Facade-level scenarios for comparing a Month with the previous one, including the Same-Point Comparison. */
class MonthComparisonScenarioTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);
    private static final Month SEPTEMBER = Month.of(2026, 9);
    private static final Month OCTOBER = Month.of(2026, 10);

    private BudgetService service = serviceOn(TODAY);

    @Test
    void aCompletedMonthIsComparedWithTheWholePreviousMonthPerCategory() {
        expense(LocalDate.of(2026, 8, 3), "FOOD", 4_000);
        expense(LocalDate.of(2026, 8, 31), "FOOD", 1_000);
        expense(LocalDate.of(2026, 8, 1), "RENT", 12_000);
        expense(LocalDate.of(2026, 9, 1), "RENT", 12_000);
        expense(LocalDate.of(2026, 9, 30), "FOOD", 6_000);
        income(LocalDate.of(2026, 9, 10), "SALARY", 30_000);
        expense(LocalDate.of(2026, 10, 1), "FOOD", 999);

        MonthComparison comparison = service.monthComparison(SEPTEMBER);

        assertEquals(Optional.empty(), comparison.samePoint());
        Comparison full = comparison.fullMonth();
        assertEquals(SEPTEMBER.firstDay(), full.from());
        assertEquals(SEPTEMBER.lastDay(), full.through());
        assertEquals(Month.of(2026, 8).firstDay(), full.previousFrom());
        assertEquals(Month.of(2026, 8).lastDay(), full.previousThrough());
        assertEquals(List.of(
                        row("RENT", 12_000, 12_000),
                        row("FOOD", 6_000, 5_000)),
                rows(full));
        assertEquals(new Change(czk(18_000), czk(17_000)), full.total());
    }

    @Test
    void theDifferenceIsGivenInCzkAndInPercentOfThePreviousMonth() {
        Change food = new Change(czk(6_000), czk(5_000));
        Change games = new Change(czk(300), czk(1_200));

        assertEquals(czk(1_000), food.difference());
        assertEquals(Optional.of(new BigDecimal("0.2000")), food.percent());
        assertEquals(czk(-900), games.difference());
        assertEquals(Optional.of(new BigDecimal("-0.7500")), games.percent());
    }

    @Test
    void thePercentIsUndefinedWhenThePreviousValueIsZero() {
        expense(LocalDate.of(2026, 9, 12), "GAMES", 1_500);

        CategoryChange games = service.monthComparison(SEPTEMBER).fullMonth().categories().getFirst();

        assertEquals(czk(1_500), games.change().difference());
        assertEquals(Optional.empty(), games.change().percent());
        assertEquals(Optional.empty(), new Change(Money.ZERO, Money.ZERO).percent());
    }

    @Test
    void aCategoryPresentInOnlyOneOfTheTwoMonthsIsShownAsZeroInTheOther() {
        expense(LocalDate.of(2026, 8, 20), "CLOTHES", 2_000);
        expense(LocalDate.of(2026, 9, 12), "GAMES", 1_500);

        assertEquals(List.of(
                        row("GAMES", 1_500, 0),
                        row("CLOTHES", 0, 2_000)),
                rows(service.monthComparison(SEPTEMBER).fullMonth()));
    }

    @Test
    void refundsLowerTheirCategoryAndIncomesAreLeftOut() {
        expense(LocalDate.of(2026, 9, 5), "ELECTRONICS", 3_000);
        expense(LocalDate.of(2026, 9, 9), "ELECTRONICS", -1_000);
        income(LocalDate.of(2026, 8, 10), "SALARY", 30_000);

        assertEquals(List.of(row("ELECTRONICS", 2_000, 0)), rows(service.monthComparison(SEPTEMBER).fullMonth()));
    }

    @Test
    void theRunningMonthComparesUpToTheSameDayFirstAndTheWholePreviousMonthAsWell() {
        expense(LocalDate.of(2026, 9, 1), "RENT", 12_000);
        expense(LocalDate.of(2026, 9, 10), "FOOD", 1_000);
        expense(LocalDate.of(2026, 9, 11), "FOOD", 4_000);
        expense(LocalDate.of(2026, 10, 1), "RENT", 12_500);
        expense(LocalDate.of(2026, 10, 10), "FOOD", 1_500);

        MonthComparison comparison = service.monthComparison(OCTOBER);

        Comparison samePoint = comparison.samePoint().orElseThrow();
        assertEquals(LocalDate.of(2026, 10, 1), samePoint.from());
        assertEquals(LocalDate.of(2026, 10, 10), samePoint.through());
        assertEquals(LocalDate.of(2026, 9, 1), samePoint.previousFrom());
        assertEquals(LocalDate.of(2026, 9, 10), samePoint.previousThrough());
        assertEquals(List.of(
                        row("RENT", 12_500, 12_000),
                        row("FOOD", 1_500, 1_000)),
                rows(samePoint));

        Comparison full = comparison.fullMonth();
        assertEquals(OCTOBER.lastDay(), full.through());
        assertEquals(SEPTEMBER.lastDay(), full.previousThrough());
        assertEquals(List.of(
                        row("RENT", 12_500, 12_000),
                        row("FOOD", 1_500, 5_000)),
                rows(full));
    }

    @Test
    void theSamePointDayIsClampedToTheLengthOfTheShorterPreviousMonth() {
        service = serviceOn(LocalDate.of(2027, 3, 31));
        expense(LocalDate.of(2027, 2, 28), "FOOD", 700);
        expense(LocalDate.of(2027, 3, 31), "FOOD", 900);

        Comparison samePoint = service.monthComparison(Month.of(2027, 3)).samePoint().orElseThrow();

        assertEquals(LocalDate.of(2027, 3, 31), samePoint.through());
        assertEquals(LocalDate.of(2027, 2, 1), samePoint.previousFrom());
        assertEquals(LocalDate.of(2027, 2, 28), samePoint.previousThrough());
        assertEquals(List.of(row("FOOD", 900, 700)), rows(samePoint));
    }

    @Test
    void inALeapYearTheSamePointDayIsClampedToFebruaryThe29th() {
        service = serviceOn(LocalDate.of(2028, 3, 30));

        Comparison samePoint = service.monthComparison(Month.of(2028, 3)).samePoint().orElseThrow();

        assertEquals(LocalDate.of(2028, 2, 29), samePoint.previousThrough());
    }

    @Test
    void aFutureMonthHasNoSamePointComparison() {
        assertEquals(Optional.empty(), service.monthComparison(Month.of(2026, 11)).samePoint());
    }

    @Test
    void twoMonthsWithoutExpensesCompareAsEmpty() {
        Comparison full = service.monthComparison(SEPTEMBER).fullMonth();

        assertTrue(full.categories().isEmpty());
        assertEquals(new Change(Money.ZERO, Money.ZERO), full.total());
    }

    private record Row(String category, Money current, Money previous) {
    }

    private Row row(String category, long current, long previous) {
        return new Row(category, czk(current), czk(previous));
    }

    private static List<Row> rows(Comparison comparison) {
        return comparison.categories().stream()
                .map(c -> new Row(c.category().name(), c.change().current(), c.change().previous()))
                .toList();
    }

    private BudgetService serviceOn(LocalDate today) {
        BudgetService fresh = new BudgetService(new InMemorySettingsRepository(), new InMemoryCategoryRepository(),
                new InMemoryTransactionRepository(), Clock.fixed(today));
        fresh.seedPresetCategories(PresetCategory::name);
        return fresh;
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
