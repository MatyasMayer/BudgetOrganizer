package org.example.budget.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Facade-level scenarios for recording and listing CZK Transactions and the preset Categories. */
class TransactionScenarioTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    private final BudgetService service = new BudgetService(
            new InMemorySettingsRepository(), new InMemoryCategoryRepository(), new InMemoryTransactionRepository(),
            new InMemoryBudgetLimitRepository(), Clock.fixed(TODAY));

    @Test
    void firstStartSeedsThePresetCategoriesInTheChosenLanguage() {
        service.seedPresetCategories(preset -> "cs:" + preset.name());

        assertEquals(List.of("cs:FOOD", "cs:RENT", "cs:TRANSPORT", "cs:GAMES", "cs:SUBSCRIPTIONS", "cs:GOING_OUT",
                        "cs:ELECTRONICS", "cs:HEALTH", "cs:CLOTHES", "cs:GIFTS", "cs:OTHER_EXPENSE"),
                names(service.categories(TransactionType.EXPENSE)));
        assertEquals(List.of("cs:SALARY", "cs:GIFT", "cs:OTHER_INCOME", "cs:FROM_SAVINGS"),
                names(service.categories(TransactionType.INCOME)));
    }

    @Test
    void fromSavingsIsTheOnlySystemCategory() {
        service.seedPresetCategories(PresetCategory::name);

        List<Category> system = service.categories(TransactionType.INCOME).stream().filter(Category::system).toList();
        assertEquals(List.of("FROM_SAVINGS"), names(system));
        assertTrue(service.categories(TransactionType.EXPENSE).stream().noneMatch(Category::system));
    }

    @Test
    void laterStartsDoNotSeedAgain() {
        service.seedPresetCategories(PresetCategory::name);
        service.seedPresetCategories(preset -> "again");

        assertEquals(11, service.categories(TransactionType.EXPENSE).size());
        assertEquals(4, service.categories(TransactionType.INCOME).size());
        assertFalse(names(service.categories(TransactionType.INCOME)).contains("again"));
    }

    @Test
    void anExpenseIsRecordedWithTodayAsTheDefaultDate() {
        service.seedPresetCategories(PresetCategory::name);
        Category food = category("FOOD");

        Transaction lunch = service.recordTransaction(new TransactionDetails(
                service.today(), TransactionType.EXPENSE, food.id(), czk(189), "Lunch – bistro"));

        assertEquals(List.of(lunch), service.transactions(TransactionFilter.ALL));
        assertEquals(TODAY, lunch.date());
        assertEquals(czk(189), lunch.amount());
        assertEquals("Lunch – bistro", lunch.note());
    }

    @Test
    void anIncomeIsRecordedWithoutANote() {
        service.seedPresetCategories(PresetCategory::name);

        Transaction salary = service.recordTransaction(new TransactionDetails(
                LocalDate.of(2026, 10, 10), TransactionType.INCOME, category("SALARY").id(), czk(30_000), "  "));

        assertEquals(TransactionType.INCOME, salary.type());
        assertFalse(salary.details().hasNote());
        assertEquals(List.of(salary), service.transactions(TransactionFilter.ALL));
    }

    @Test
    void aTransactionNeedsACategoryOfItsOwnType() {
        service.seedPresetCategories(PresetCategory::name);

        InvalidTransactionException error = assertThrows(InvalidTransactionException.class, () ->
                service.recordTransaction(new TransactionDetails(
                        TODAY, TransactionType.INCOME, category("FOOD").id(), czk(30_000), "")));

        assertEquals(InvalidTransactionException.Problem.CATEGORY_OF_OTHER_TYPE, error.problem());
        assertEquals(List.of(), service.transactions(TransactionFilter.ALL));
    }

    @Test
    void aTransactionNeedsAnExistingCategory() {
        InvalidTransactionException error = assertThrows(InvalidTransactionException.class, () ->
                service.recordTransaction(new TransactionDetails(TODAY, TransactionType.EXPENSE, 999, czk(5), "")));

        assertEquals(InvalidTransactionException.Problem.UNKNOWN_CATEGORY, error.problem());
    }

    @Test
    void anAmountOfZeroIsRejected() {
        service.seedPresetCategories(PresetCategory::name);

        InvalidTransactionException error = assertThrows(InvalidTransactionException.class, () ->
                service.recordTransaction(new TransactionDetails(
                        TODAY, TransactionType.EXPENSE, category("FOOD").id(), Money.ZERO, "")));

        assertEquals(InvalidTransactionException.Problem.ZERO_AMOUNT, error.problem());
    }

    @Test
    void anIncomeCannotBeNegative() {
        service.seedPresetCategories(PresetCategory::name);

        InvalidTransactionException error = assertThrows(InvalidTransactionException.class, () ->
                service.recordTransaction(new TransactionDetails(
                        TODAY, TransactionType.INCOME, category("GIFT").id(), czk(-500), "")));

        assertEquals(InvalidTransactionException.Problem.NEGATIVE_INCOME, error.problem());
    }

    @Test
    void aRefundIsANegativeExpenseThatLowersItsCategorysTotal() {
        service.seedPresetCategories(PresetCategory::name);
        long clothes = category("CLOTHES").id();
        expense(LocalDate.of(2026, 10, 3), clothes, 2_000, "Jacket");
        expense(LocalDate.of(2026, 10, 5), clothes, 600, "Shoes");

        Transaction refund = expense(LocalDate.of(2026, 10, 9), clothes, -600, "Shoes returned");

        assertTrue(refund.isRefund());
        assertEquals(czk(2_000), service.categoryTotal(clothes, Month.of(2026, 10)));
    }

    @Test
    void aCategoryTotalCountsOnlyItsOwnMonth() {
        service.seedPresetCategories(PresetCategory::name);
        long food = category("FOOD").id();
        expense(LocalDate.of(2026, 9, 30), food, 400, "");
        expense(LocalDate.of(2026, 10, 1), food, 250, "");
        expense(LocalDate.of(2026, 10, 1), category("RENT").id(), 12_000, "");

        assertEquals(czk(250), service.categoryTotal(food, Month.of(2026, 10)));
        assertEquals(czk(400), service.categoryTotal(food, Month.of(2026, 9)));
        assertEquals(Money.ZERO, service.categoryTotal(food, Month.of(2026, 8)));
    }

    @Test
    void aTransactionInAPastMonthCanBeEditedInEveryField() {
        service.seedPresetCategories(PresetCategory::name);
        Transaction receipt = expense(LocalDate.of(2026, 7, 12), category("FOOD").id(), 300, "Groceries");

        TransactionDetails corrected = new TransactionDetails(LocalDate.of(2026, 6, 28), TransactionType.EXPENSE,
                category("HEALTH").id(), czk(350), "Pharmacy");
        service.editTransaction(receipt.id(), corrected);

        assertEquals(List.of(new Transaction(receipt.id(), corrected)), service.transactions(TransactionFilter.ALL));
        assertEquals(Money.ZERO, service.categoryTotal(category("FOOD").id(), Month.of(2026, 7)));
        assertEquals(czk(350), service.categoryTotal(category("HEALTH").id(), Month.of(2026, 6)));
    }

    @Test
    void anEditCanTurnAnExpenseIntoAnIncome() {
        service.seedPresetCategories(PresetCategory::name);
        Transaction mistake = expense(TODAY, category("GIFTS").id(), 1_000, "From grandma");

        service.editTransaction(mistake.id(), new TransactionDetails(
                TODAY, TransactionType.INCOME, category("GIFT").id(), czk(1_000), "From grandma"));

        assertEquals(TransactionType.INCOME, service.transactions(TransactionFilter.ALL).getFirst().type());
    }

    @Test
    void anEditIsValidatedLikeANewTransaction() {
        service.seedPresetCategories(PresetCategory::name);
        Transaction lunch = expense(TODAY, category("FOOD").id(), 189, "Lunch");

        assertThrows(InvalidTransactionException.class, () -> service.editTransaction(lunch.id(),
                new TransactionDetails(TODAY, TransactionType.INCOME, category("FOOD").id(), czk(189), "")));

        assertEquals(List.of(lunch), service.transactions(TransactionFilter.ALL));
    }

    @Test
    void editingAnUnknownTransactionIsRejected() {
        service.seedPresetCategories(PresetCategory::name);

        InvalidTransactionException error = assertThrows(InvalidTransactionException.class, () ->
                service.editTransaction(42, new TransactionDetails(
                        TODAY, TransactionType.EXPENSE, category("FOOD").id(), czk(1), "")));

        assertEquals(InvalidTransactionException.Problem.UNKNOWN_TRANSACTION, error.problem());
    }

    @Test
    void aTransactionInAPastMonthCanBeDeleted() {
        service.seedPresetCategories(PresetCategory::name);
        long food = category("FOOD").id();
        Transaction duplicate = expense(LocalDate.of(2025, 12, 24), food, 900, "Christmas dinner");
        Transaction original = expense(LocalDate.of(2025, 12, 24), food, 900, "Christmas dinner");

        service.deleteTransaction(duplicate.id());

        assertEquals(List.of(original), service.transactions(TransactionFilter.ALL));
        assertEquals(czk(900), service.categoryTotal(food, Month.of(2025, 12)));
    }

    @Test
    void theListIsNewestFirst() {
        service.seedPresetCategories(PresetCategory::name);
        long food = category("FOOD").id();
        Transaction older = expense(LocalDate.of(2026, 10, 2), food, 1, "");
        Transaction newer = expense(LocalDate.of(2026, 10, 15), food, 2, "");
        Transaction sameDayLater = expense(LocalDate.of(2026, 10, 15), food, 3, "");

        assertEquals(List.of(sameDayLater, newer, older), service.transactions(TransactionFilter.ALL));
    }

    @Test
    void theListFiltersByMonthCategoryAndType() {
        service.seedPresetCategories(PresetCategory::name);
        long food = category("FOOD").id();
        long games = category("GAMES").id();
        Transaction septemberFood = expense(LocalDate.of(2026, 9, 20), food, 500, "");
        Transaction octoberFood = expense(LocalDate.of(2026, 10, 4), food, 300, "");
        Transaction octoberGame = expense(LocalDate.of(2026, 10, 8), games, 1_200, "Steam – Hades II");
        Transaction salary = service.recordTransaction(new TransactionDetails(
                LocalDate.of(2026, 10, 10), TransactionType.INCOME, category("SALARY").id(), czk(30_000), ""));

        TransactionFilter october = TransactionFilter.ALL.inMonth(Month.of(2026, 10));
        assertEquals(List.of(salary, octoberGame, octoberFood), service.transactions(october));
        assertEquals(List.of(octoberFood, septemberFood), service.transactions(TransactionFilter.ALL.inCategory(food)));
        assertEquals(List.of(octoberGame, octoberFood, septemberFood),
                service.transactions(TransactionFilter.ALL.ofType(TransactionType.EXPENSE)));
        assertEquals(List.of(octoberFood), service.transactions(october.inCategory(food)));
        assertEquals(List.of(salary), service.transactions(october.ofType(TransactionType.INCOME)));
    }

    @Test
    void theNoteSearchIgnoresCaseAndCombinesWithOtherFilters() {
        service.seedPresetCategories(PresetCategory::name);
        long games = category("GAMES").id();
        Transaction hades = expense(LocalDate.of(2026, 10, 8), games, 600, "Steam – Hades II");
        Transaction summerSale = expense(LocalDate.of(2026, 7, 1), games, 400, "STEAM summer sale");
        expense(LocalDate.of(2026, 10, 9), games, 1_500, "PlayStation Store");
        Transaction czech = expense(LocalDate.of(2026, 10, 11), category("FOOD").id(), 120, "Čerstvé PEČIVO");

        assertEquals(List.of(hades, summerSale),
                service.transactions(TransactionFilter.ALL.withNoteContaining("steam")));
        assertEquals(List.of(hades), service.transactions(
                TransactionFilter.ALL.withNoteContaining("steam").inMonth(Month.of(2026, 10))));
        assertEquals(List.of(czech), service.transactions(TransactionFilter.ALL.withNoteContaining("pečivo")));
        assertEquals(List.of(), service.transactions(TransactionFilter.ALL.withNoteContaining("netflix")));
    }

    private Transaction expense(LocalDate date, long categoryId, long crowns, String note) {
        return service.recordTransaction(
                new TransactionDetails(date, TransactionType.EXPENSE, categoryId, czk(crowns), note));
    }

    private Category category(String name) {
        return java.util.stream.Stream.of(TransactionType.values())
                .flatMap(type -> service.categories(type).stream())
                .filter(category -> category.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static Money czk(long crowns) {
        return Money.ofHalere(crowns * 100);
    }

    private static List<String> names(List<Category> list) {
        return list.stream().map(Category::name).toList();
    }
}
