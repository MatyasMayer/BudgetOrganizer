package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Facade-level scenarios for managing Categories: add, rename, delete and merge-on-delete. */
class CategoryManagementScenarioTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    private final BudgetService service = new BudgetService(
            new InMemorySettingsRepository(), new InMemoryCategoryRepository(), new InMemoryTransactionRepository(),
            new InMemoryRecurringTemplateRepository(),
            new InMemoryBudgetLimitRepository(), Clock.fixed(TODAY));

    @BeforeEach
    void seed() {
        service.seedPresetCategories(PresetCategory::name);
    }

    @Test
    void anAddedCategoryJoinsTheListOfItsTypeAndCanBeUsedRightAway() {
        Category pets = service.addCategory("Pets", TransactionType.EXPENSE);

        assertEquals("Pets", names(service.categories(TransactionType.EXPENSE)).getLast());
        assertEquals(false, names(service.categories(TransactionType.INCOME)).contains("Pets"));
        Transaction food = expense(pets.id(), 450, "Cat food");
        assertEquals(List.of(food), service.transactions(TransactionFilter.ALL.inCategory(pets.id())));
    }

    @Test
    void aNewCategoryNeedsAName() {
        InvalidCategoryException error = assertThrows(InvalidCategoryException.class,
                () -> service.addCategory("   ", TransactionType.EXPENSE));

        assertEquals(InvalidCategoryException.Problem.BLANK_NAME, error.problem());
        assertEquals(11, service.categories(TransactionType.EXPENSE).size());
    }

    @Test
    void aNameIsStoredWithoutSurroundingSpaces() {
        assertEquals("Pets", service.addCategory("  Pets ", TransactionType.EXPENSE).name());
    }

    @Test
    void twoCategoriesOfOneTypeCannotShareANameIgnoringCase() {
        InvalidCategoryException error = assertThrows(InvalidCategoryException.class,
                () -> service.addCategory("food", TransactionType.EXPENSE));

        assertEquals(InvalidCategoryException.Problem.DUPLICATE_NAME, error.problem());
    }

    @Test
    void anIncomeAndAnExpenseCategoryMayShareAName() {
        Category food = service.addCategory("FOOD", TransactionType.INCOME);

        assertEquals(TransactionType.INCOME, food.type());
    }

    @Test
    void aRenamedCategoryKeepsAllItsTransactions() {
        long games = category("GAMES").id();
        Transaction hades = expense(games, 600, "Steam – Hades II");
        Transaction sale = expense(games, 400, "Summer sale");

        service.renameCategory(games, "Video games");

        assertEquals("Video games", category("Video games").name());
        assertEquals(games, category("Video games").id());
        assertEquals(List.of(sale, hades), service.transactions(TransactionFilter.ALL.inCategory(games)));
        assertEquals(Money.ofHalere(100_000), service.categoryTotal(games, Month.of(TODAY)));
    }

    @Test
    void aRenameIsValidatedLikeANewName() {
        long games = category("GAMES").id();

        assertEquals(InvalidCategoryException.Problem.BLANK_NAME, assertThrows(InvalidCategoryException.class,
                () -> service.renameCategory(games, "")).problem());
        assertEquals(InvalidCategoryException.Problem.DUPLICATE_NAME, assertThrows(InvalidCategoryException.class,
                () -> service.renameCategory(games, "Food")).problem());
        assertEquals("GAMES", service.categories(TransactionType.EXPENSE).get(3).name());
    }

    @Test
    void aCategoryCanBeRenamedToADifferentCaseOfItsOwnName() {
        long games = category("GAMES").id();

        service.renameCategory(games, "Games");

        assertEquals(games, category("Games").id());
    }

    @Test
    void fromSavingsCanBeRenamedAndStaysTheSystemCategory() {
        long fromSavings = category("FROM_SAVINGS").id();

        service.renameCategory(fromSavings, "Savings withdrawal");

        assertEquals(true, category("Savings withdrawal").system());
    }

    @Test
    void renamingAnUnknownCategoryIsRejected() {
        assertEquals(InvalidCategoryException.Problem.UNKNOWN_CATEGORY, assertThrows(InvalidCategoryException.class,
                () -> service.renameCategory(999, "Ghost")).problem());
    }

    @Test
    void aCategoryWithoutTransactionsIsDeletedImmediately() {
        Category pets = service.addCategory("Pets", TransactionType.EXPENSE);
        assertFalse(service.hasTransactions(pets.id()));

        service.deleteCategory(pets.id());

        assertFalse(names(service.categories(TransactionType.EXPENSE)).contains("Pets"));
    }

    @Test
    void aCategoryWithTransactionsCannotBeDeletedWithoutAReplacement() {
        long games = category("GAMES").id();
        expense(games, 600, "Steam – Hades II");
        assertTrue(service.hasTransactions(games));

        InvalidCategoryException error = assertThrows(InvalidCategoryException.class,
                () -> service.deleteCategory(games));

        assertEquals(InvalidCategoryException.Problem.REPLACEMENT_REQUIRED, error.problem());
        assertEquals(games, category("GAMES").id());
    }

    @Test
    void deletingWithAReplacementMovesEveryTransactionThereFromEveryMonth() {
        long games = category("GAMES").id();
        long other = category("OTHER_EXPENSE").id();
        Transaction hades = expense(games, 600, "Steam – Hades II");
        Transaction old = service.recordTransaction(new TransactionDetails(LocalDate.of(2025, 12, 24),
                TransactionType.EXPENSE, games, Money.ofHalere(150_000), "Christmas game"));
        Transaction umbrella = expense(other, 300, "Umbrella");

        service.deleteCategory(games, other);

        assertFalse(names(service.categories(TransactionType.EXPENSE)).contains("GAMES"));
        assertEquals(List.of(umbrella.id(), hades.id(), old.id()),
                service.transactions(TransactionFilter.ALL.inCategory(other)).stream().map(Transaction::id).toList());
        assertEquals(Money.ofHalere(90_000), service.categoryTotal(other, Month.of(TODAY)));
        assertEquals(Money.ofHalere(150_000), service.categoryTotal(other, Month.of(2025, 12)));
    }

    @Test
    void aCategoryWithoutTransactionsMayAlsoBeDeletedWithAReplacement() {
        Category pets = service.addCategory("Pets", TransactionType.EXPENSE);

        service.deleteCategory(pets.id(), category("OTHER_EXPENSE").id());

        assertFalse(names(service.categories(TransactionType.EXPENSE)).contains("Pets"));
    }

    @Test
    void theReplacementMustBeOfTheSameType() {
        long games = category("GAMES").id();
        Transaction hades = expense(games, 600, "Steam – Hades II");

        InvalidCategoryException error = assertThrows(InvalidCategoryException.class,
                () -> service.deleteCategory(games, category("SALARY").id()));

        assertEquals(InvalidCategoryException.Problem.REPLACEMENT_OF_OTHER_TYPE, error.problem());
        assertEquals(List.of(hades), service.transactions(TransactionFilter.ALL.inCategory(games)));
    }

    @Test
    void theReplacementMustBeAnotherExistingCategory() {
        long games = category("GAMES").id();
        expense(games, 600, "");

        assertEquals(InvalidCategoryException.Problem.REPLACEMENT_IS_SAME, assertThrows(InvalidCategoryException.class,
                () -> service.deleteCategory(games, games)).problem());
        assertEquals(InvalidCategoryException.Problem.UNKNOWN_CATEGORY, assertThrows(InvalidCategoryException.class,
                () -> service.deleteCategory(games, 999)).problem());
        assertEquals(games, category("GAMES").id());
    }

    @Test
    void deletingAnUnknownCategoryIsRejected() {
        assertEquals(InvalidCategoryException.Problem.UNKNOWN_CATEGORY, assertThrows(InvalidCategoryException.class,
                () -> service.deleteCategory(999)).problem());
    }

    @Test
    void fromSavingsCannotBeDeleted() {
        long fromSavings = category("FROM_SAVINGS").id();

        assertEquals(InvalidCategoryException.Problem.SYSTEM_CATEGORY, assertThrows(InvalidCategoryException.class,
                () -> service.deleteCategory(fromSavings)).problem());
        assertTrue(category("FROM_SAVINGS").system());
    }

    @Test
    void fromSavingsCannotBeMergedIntoAnotherCategory() {
        long fromSavings = category("FROM_SAVINGS").id();
        Transaction withdrawal = service.recordTransaction(new TransactionDetails(
                TODAY, TransactionType.INCOME, fromSavings, Money.ofHalere(500_000), "New PC"));

        assertEquals(InvalidCategoryException.Problem.SYSTEM_CATEGORY, assertThrows(InvalidCategoryException.class,
                () -> service.deleteCategory(fromSavings, category("GIFT").id())).problem());
        assertEquals(List.of(withdrawal), service.transactions(TransactionFilter.ALL.inCategory(fromSavings)));
    }

    private Transaction expense(long categoryId, long crowns, String note) {
        return service.recordTransaction(
                new TransactionDetails(TODAY, TransactionType.EXPENSE, categoryId, Money.ofHalere(crowns * 100), note));
    }

    private Category category(String name) {
        return java.util.stream.Stream.of(TransactionType.values())
                .flatMap(type -> service.categories(type).stream())
                .filter(category -> category.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> names(List<Category> list) {
        return list.stream().map(Category::name).toList();
    }
}
