package org.example.budget.domain;

import org.example.budget.domain.MonthSummary.CategorySpending;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Facade-level scenarios for Budget Limits: Default Limits, Limit Overrides, bar status and merging. */
class BudgetLimitScenarioTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
    private static final Month OCTOBER = Month.of(2026, 10);
    private static final Month NOVEMBER = Month.of(2026, 11);
    private static final Month DECEMBER = Month.of(2026, 12);

    private final BudgetService service = new BudgetService(
            new InMemorySettingsRepository(), new InMemoryCategoryRepository(), new InMemoryTransactionRepository(),
            new InMemoryBudgetLimitRepository(), Clock.fixed(TODAY));

    @BeforeEach
    void seed() {
        service.seedPresetCategories(PresetCategory::name);
    }

    @Test
    void aCategoryWithoutBudgetLimitsHasNoLimitInAnyMonth() {
        assertEquals(Optional.empty(), service.effectiveLimit(id("FOOD"), OCTOBER));
    }

    @Test
    void aDefaultLimitAppliesToEveryMonth() {
        service.setDefaultLimit(id("FOOD"), czk(6_000));

        assertEquals(Optional.of(czk(6_000)), service.defaultLimit(id("FOOD")));
        assertEquals(Optional.of(czk(6_000)), service.effectiveLimit(id("FOOD"), OCTOBER));
        assertEquals(Optional.of(czk(6_000)), service.effectiveLimit(id("FOOD"), Month.of(2025, 3)));
    }

    @Test
    void aLimitOverrideReplacesTheDefaultLimitInItsMonthOnly() {
        service.setDefaultLimit(id("FOOD"), czk(6_000));

        service.setLimitOverride(id("FOOD"), DECEMBER, czk(9_000));

        assertEquals(Optional.of(czk(9_000)), service.limitOverride(id("FOOD"), DECEMBER));
        assertEquals(Optional.of(czk(9_000)), service.effectiveLimit(id("FOOD"), DECEMBER));
        assertEquals(Optional.of(czk(6_000)), service.effectiveLimit(id("FOOD"), NOVEMBER));
        assertEquals(Optional.empty(), service.limitOverride(id("FOOD"), NOVEMBER));
    }

    @Test
    void aLimitOverrideWorksWithoutADefaultLimit() {
        service.setLimitOverride(id("GIFTS"), DECEMBER, czk(4_000));

        assertEquals(Optional.of(czk(4_000)), service.effectiveLimit(id("GIFTS"), DECEMBER));
        assertEquals(Optional.empty(), service.effectiveLimit(id("GIFTS"), NOVEMBER));
    }

    @Test
    void clearingALimitOverrideFallsBackToTheDefaultLimit() {
        service.setDefaultLimit(id("FOOD"), czk(6_000));
        service.setLimitOverride(id("FOOD"), DECEMBER, czk(9_000));

        service.clearLimitOverride(id("FOOD"), DECEMBER);

        assertEquals(Optional.of(czk(6_000)), service.effectiveLimit(id("FOOD"), DECEMBER));
    }

    @Test
    void clearingTheDefaultLimitLeavesNoLimitExceptInMonthsWithALimitOverride() {
        service.setDefaultLimit(id("FOOD"), czk(6_000));
        service.setLimitOverride(id("FOOD"), DECEMBER, czk(9_000));

        service.clearDefaultLimit(id("FOOD"));

        assertEquals(Optional.empty(), service.defaultLimit(id("FOOD")));
        assertEquals(Optional.empty(), service.effectiveLimit(id("FOOD"), NOVEMBER));
        assertEquals(Optional.of(czk(9_000)), service.effectiveLimit(id("FOOD"), DECEMBER));
    }

    @Test
    void settingALimitAgainReplacesIt() {
        service.setDefaultLimit(id("FOOD"), czk(6_000));
        service.setLimitOverride(id("FOOD"), DECEMBER, czk(9_000));

        service.setDefaultLimit(id("FOOD"), czk(6_500));
        service.setLimitOverride(id("FOOD"), DECEMBER, czk(8_000));

        assertEquals(Optional.of(czk(6_500)), service.effectiveLimit(id("FOOD"), NOVEMBER));
        assertEquals(Optional.of(czk(8_000)), service.effectiveLimit(id("FOOD"), DECEMBER));
    }

    @Test
    void incomeCategoriesCannotHaveBudgetLimits() {
        long salary = id("SALARY");

        assertEquals(InvalidBudgetLimitException.Problem.INCOME_CATEGORY, assertThrows(
                InvalidBudgetLimitException.class, () -> service.setDefaultLimit(salary, czk(30_000))).problem());
        assertEquals(InvalidBudgetLimitException.Problem.INCOME_CATEGORY, assertThrows(
                InvalidBudgetLimitException.class,
                () -> service.setLimitOverride(salary, DECEMBER, czk(30_000))).problem());
        assertEquals(Optional.empty(), service.effectiveLimit(salary, DECEMBER));
    }

    @Test
    void aBudgetLimitMustBeMoreThanZero() {
        long food = id("FOOD");

        assertEquals(InvalidBudgetLimitException.Problem.NOT_POSITIVE, assertThrows(
                InvalidBudgetLimitException.class, () -> service.setDefaultLimit(food, Money.ZERO)).problem());
        assertEquals(InvalidBudgetLimitException.Problem.NOT_POSITIVE, assertThrows(
                InvalidBudgetLimitException.class,
                () -> service.setLimitOverride(food, DECEMBER, czk(-100))).problem());
        assertEquals(Optional.empty(), service.effectiveLimit(food, DECEMBER));
    }

    @Test
    void anUnknownCategoryCannotGetABudgetLimit() {
        assertEquals(InvalidBudgetLimitException.Problem.UNKNOWN_CATEGORY, assertThrows(
                InvalidBudgetLimitException.class, () -> service.setDefaultLimit(999, czk(1_000))).problem());
    }

    @Test
    void theDashboardShowsSpendingAgainstTheEffectiveLimit() {
        service.setDefaultLimit(id("FOOD"), czk(6_000));
        service.setLimitOverride(id("FOOD"), OCTOBER, czk(7_000));
        expense(LocalDate.of(2026, 10, 3), "FOOD", 2_500);
        expense(LocalDate.of(2026, 10, 9), "FOOD", 1_000);

        CategorySpending food = spending(OCTOBER, "FOOD");

        assertEquals(czk(3_500), food.total());
        assertEquals(Optional.of(czk(7_000)), food.limit());
        assertEquals(Optional.of(BudgetBarStatus.GREEN), food.barStatus());
    }

    @Test
    void theBarIsGreenBelow80PercentOrangeFrom80To100PercentAndRedAbove() {
        service.setDefaultLimit(id("FOOD"), czk(5_000));
        Transaction lunch = expense(LocalDate.of(2026, 10, 3), "FOOD", 100);

        assertEquals(BudgetBarStatus.GREEN, barWhenSpent(lunch, Money.ofHalere(399_999)));
        assertEquals(BudgetBarStatus.ORANGE, barWhenSpent(lunch, czk(4_000)));
        assertEquals(BudgetBarStatus.ORANGE, barWhenSpent(lunch, czk(5_000)));
        assertEquals(BudgetBarStatus.RED, barWhenSpent(lunch, Money.ofHalere(500_001)));
        assertEquals(BudgetBarStatus.GREEN, barWhenSpent(lunch, czk(-200)));
    }

    @Test
    void aCategoryWithoutALimitShowsSpendingOnlyWithNoBar() {
        service.setDefaultLimit(id("FOOD"), czk(6_000));
        expense(LocalDate.of(2026, 10, 2), "GAMES", 1_200);

        CategorySpending games = spending(OCTOBER, "GAMES");

        assertEquals(czk(1_200), games.total());
        assertEquals(Optional.empty(), games.limit());
        assertEquals(Optional.empty(), games.barStatus());
    }

    @Test
    void aLimitedCategoryWithNothingSpentYetStillShowsAnEmptyBar() {
        service.setDefaultLimit(id("FOOD"), czk(6_000));
        expense(LocalDate.of(2026, 10, 1), "RENT", 12_000);

        MonthSummary summary = service.monthSummary(OCTOBER);

        assertEquals(List.of(
                        new CategorySpending(category("RENT"), czk(12_000), Optional.empty()),
                        new CategorySpending(category("FOOD"), Money.ZERO, Optional.of(czk(6_000)))),
                summary.expensesByCategory());
        assertEquals(czk(12_000), summary.expenses());
    }

    @Test
    void unspentLimitDoesNotRollOverIntoTheNextMonth() {
        service.setDefaultLimit(id("FOOD"), czk(5_000));
        expense(LocalDate.of(2026, 9, 10), "FOOD", 3_000);
        expense(LocalDate.of(2026, 10, 10), "FOOD", 5_500);

        CategorySpending october = spending(OCTOBER, "FOOD");

        assertEquals(Optional.of(czk(5_000)), october.limit());
        assertEquals(Optional.of(BudgetBarStatus.RED), october.barStatus());
        assertEquals(Optional.of(czk(5_000)), service.effectiveLimit(id("FOOD"), OCTOBER));
    }

    @Test
    void deletingACategoryMovesItsBudgetLimitsToTheReplacementExceptWhereItHasItsOwn() {
        long food = id("FOOD");
        long other = id("OTHER_EXPENSE");
        service.setDefaultLimit(food, czk(6_000));
        service.setLimitOverride(food, OCTOBER, czk(7_000));
        service.setLimitOverride(food, DECEMBER, czk(9_000));
        service.setDefaultLimit(other, czk(2_000));
        service.setLimitOverride(other, OCTOBER, czk(1_500));
        expense(LocalDate.of(2026, 10, 3), "FOOD", 1_000);

        service.deleteCategory(food, other);

        assertEquals(Optional.of(czk(2_000)), service.defaultLimit(other));
        assertEquals(Optional.of(czk(1_500)), service.limitOverride(other, OCTOBER));
        assertEquals(Optional.of(czk(9_000)), service.limitOverride(other, DECEMBER));
        assertEquals(Optional.of(czk(2_000)), service.effectiveLimit(other, NOVEMBER));
        assertEquals(Optional.empty(), service.effectiveLimit(food, DECEMBER));
    }

    @Test
    void aReplacementWithoutLimitsTakesOverAllOfThem() {
        long food = id("FOOD");
        long other = id("OTHER_EXPENSE");
        service.setDefaultLimit(food, czk(6_000));
        service.setLimitOverride(food, DECEMBER, czk(9_000));

        service.deleteCategory(food, other);

        assertEquals(Optional.of(czk(6_000)), service.effectiveLimit(other, NOVEMBER));
        assertEquals(Optional.of(czk(9_000)), service.effectiveLimit(other, DECEMBER));
        assertEquals(Optional.of(czk(6_000)), spending(OCTOBER, "OTHER_EXPENSE").limit());
    }

    @Test
    void aDeletedCategoryWithoutTransactionsTakesItsBudgetLimitsWithIt() {
        Category pets = service.addCategory("Pets", TransactionType.EXPENSE);
        service.setDefaultLimit(pets.id(), czk(800));

        service.deleteCategory(pets.id());

        assertEquals(Optional.empty(), service.effectiveLimit(pets.id(), OCTOBER));
        assertEquals(List.of(), service.monthSummary(OCTOBER).expensesByCategory());
    }

    private BudgetBarStatus barWhenSpent(Transaction transaction, Money amount) {
        TransactionDetails d = transaction.details();
        service.editTransaction(transaction.id(),
                new TransactionDetails(d.date(), d.type(), d.categoryId(), amount, d.note()));
        return spending(Month.of(d.date()), category(d.categoryId()).name()).barStatus().orElseThrow();
    }

    private CategorySpending spending(Month month, String name) {
        return service.monthSummary(month).expensesByCategory().stream()
                .filter(spending -> spending.category().name().equals(name))
                .findFirst().orElseThrow();
    }

    private Transaction expense(LocalDate date, String category, long czk) {
        return service.recordTransaction(
                new TransactionDetails(date, TransactionType.EXPENSE, id(category), czk(czk), ""));
    }

    private Category category(String name) {
        return category(id(name));
    }

    private Category category(long id) {
        return java.util.stream.Stream.of(TransactionType.values())
                .flatMap(type -> service.categories(type).stream())
                .filter(category -> category.id() == id)
                .findFirst().orElseThrow();
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
