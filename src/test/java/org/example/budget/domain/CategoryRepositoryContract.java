package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** The shared suite every {@link CategoryRepository} must pass (in-memory fake and SQLite). */
public abstract class CategoryRepositoryContract {

    private CategoryRepository repository;

    /** A fresh, empty repository for each test. */
    protected abstract CategoryRepository createRepository() throws Exception;

    @BeforeEach
    void setUp() throws Exception {
        repository = createRepository();
    }

    @Test
    void aNewRepositoryHasNoCategories() {
        assertEquals(List.of(), repository.findAll());
    }

    @Test
    void anAddedCategoryGetsAnIdAndCanBeFound() {
        Category food = repository.add("Food", TransactionType.EXPENSE, false);

        assertEquals(new Category(food.id(), "Food", TransactionType.EXPENSE, false), food);
        assertEquals(Optional.of(food), repository.find(food.id()));
    }

    @Test
    void eachCategoryGetsItsOwnId() {
        Category other = repository.add("Other", TransactionType.EXPENSE, false);
        Category otherIncome = repository.add("Other", TransactionType.INCOME, false);

        assertNotEquals(other.id(), otherIncome.id());
    }

    @Test
    void anUnknownIdIsAbsent() {
        assertEquals(Optional.empty(), repository.find(12345));
    }

    @Test
    void theSystemMarkerIsKept() {
        Category fromSavings = repository.add("From Savings", TransactionType.INCOME, true);

        assertEquals(true, repository.find(fromSavings.id()).orElseThrow().system());
    }

    @Test
    void allCategoriesComeInTheOrderTheyWereAdded() {
        Category salary = repository.add("Salary", TransactionType.INCOME, false);
        Category rent = repository.add("Rent", TransactionType.EXPENSE, false);
        Category food = repository.add("Food", TransactionType.EXPENSE, false);

        assertEquals(List.of(salary, rent, food), repository.findAll());
    }

    @Test
    void aRenameChangesOnlyTheNameOfThatCategory() {
        Category games = repository.add("Games", TransactionType.EXPENSE, false);
        Category food = repository.add("Food", TransactionType.EXPENSE, false);

        repository.rename(games.id(), "Hry a zábava");

        assertEquals(List.of(new Category(games.id(), "Hry a zábava", TransactionType.EXPENSE, false), food),
                repository.findAll());
    }

    @Test
    void aDeletedCategoryIsGone() {
        Category keep = repository.add("Food", TransactionType.EXPENSE, false);
        Category remove = repository.add("Games", TransactionType.EXPENSE, false);

        repository.delete(remove.id());

        assertEquals(Optional.empty(), repository.find(remove.id()));
        assertEquals(List.of(keep), repository.findAll());
    }

    @Test
    void namesKeepCzechLetters() {
        Category category = repository.add("Oblečení a dárky – žluťoučký kůň", TransactionType.EXPENSE, false);

        assertEquals("Oblečení a dárky – žluťoučký kůň", repository.find(category.id()).orElseThrow().name());
    }
}
