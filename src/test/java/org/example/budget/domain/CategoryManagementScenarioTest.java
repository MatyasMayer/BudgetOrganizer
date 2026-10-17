package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Facade-level scenarios for managing Categories: add, rename, delete and merge-on-delete. */
class CategoryManagementScenarioTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    private final BudgetService service = new BudgetService(
            new InMemorySettingsRepository(), new InMemoryCategoryRepository(), new InMemoryTransactionRepository(),
            Clock.fixed(TODAY));

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
