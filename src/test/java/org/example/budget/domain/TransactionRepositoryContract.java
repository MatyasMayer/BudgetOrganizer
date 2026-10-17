package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The shared suite every {@link TransactionRepository} must pass (in-memory fake and SQLite).
 * Categories come from the matching {@link CategoryRepository}, since SQLite enforces the reference.
 */
public abstract class TransactionRepositoryContract {

    private TransactionRepository repository;
    private long food;
    private long games;
    private long salary;

    /** Fresh, empty repositories sharing one store. */
    protected abstract Repositories createRepositories() throws Exception;

    public record Repositories(CategoryRepository categories, TransactionRepository transactions) {
    }

    @BeforeEach
    void setUp() throws Exception {
        Repositories repositories = createRepositories();
        repository = repositories.transactions();
        food = repositories.categories().add("Food", TransactionType.EXPENSE, false).id();
        games = repositories.categories().add("Games", TransactionType.EXPENSE, false).id();
        salary = repositories.categories().add("Salary", TransactionType.INCOME, false).id();
    }

    @Test
    void aNewRepositoryHasNoTransactions() {
        assertEquals(List.of(), repository.find(TransactionFilter.ALL));
    }

    @Test
    void anAddedTransactionGetsAnIdAndCanBeFound() {
        TransactionDetails details = expense(LocalDate.of(2026, 10, 17), food, 18_950, "Lunch – bistro");

        Transaction lunch = repository.add(details);

        assertEquals(new Transaction(lunch.id(), details), lunch);
        assertEquals(Optional.of(lunch), repository.find(lunch.id()));
    }

    @Test
    void eachTransactionGetsItsOwnId() {
        Transaction first = repository.add(expense(LocalDate.of(2026, 10, 1), food, 100, ""));
        Transaction second = repository.add(expense(LocalDate.of(2026, 10, 1), food, 100, ""));

        assertNotEquals(first.id(), second.id());
    }

    @Test
    void anUnknownIdIsAbsent() {
        assertEquals(Optional.empty(), repository.find(999));
    }

    @Test
    void aMissingNoteComesBackEmpty() {
        Transaction transaction = repository.add(expense(LocalDate.of(2026, 10, 1), food, 100, null));

        assertEquals("", repository.find(transaction.id()).orElseThrow().note());
    }

    @Test
    void negativeAmountsAndIncomesAreKept() {
        Transaction refund = repository.add(expense(LocalDate.of(2026, 10, 2), food, -60_050, "Returned"));
        Transaction pay = repository.add(new TransactionDetails(
                LocalDate.of(2026, 10, 10), TransactionType.INCOME, salary, Money.ofHalere(3_000_000), ""));

        assertEquals(Money.ofHalere(-60_050), repository.find(refund.id()).orElseThrow().amount());
        assertEquals(TransactionType.INCOME, repository.find(pay.id()).orElseThrow().type());
    }

    @Test
    void anUpdateReplacesEveryField() {
        Transaction original = repository.add(expense(LocalDate.of(2026, 7, 12), food, 30_000, "Groceries"));

        Transaction changed = new Transaction(original.id(), new TransactionDetails(
                LocalDate.of(2026, 6, 1), TransactionType.INCOME, salary, Money.ofHalere(1), "Bonus"));
        repository.update(changed);

        assertEquals(Optional.of(changed), repository.find(original.id()));
    }

    @Test
    void anUpdateLeavesOtherTransactionsAlone() {
        Transaction first = repository.add(expense(LocalDate.of(2026, 10, 1), food, 100, "a"));
        Transaction second = repository.add(expense(LocalDate.of(2026, 10, 2), food, 200, "b"));

        repository.update(new Transaction(first.id(), expense(LocalDate.of(2026, 10, 1), food, 150, "a")));

        assertEquals(Optional.of(second), repository.find(second.id()));
    }

    @Test
    void aDeletedTransactionIsGone() {
        Transaction keep = repository.add(expense(LocalDate.of(2026, 10, 1), food, 100, ""));
        Transaction remove = repository.add(expense(LocalDate.of(2026, 10, 1), food, 100, ""));

        repository.delete(remove.id());

        assertEquals(Optional.empty(), repository.find(remove.id()));
        assertEquals(List.of(keep), repository.find(TransactionFilter.ALL));
    }

    @Test
    void theListIsNewestDateFirstThenNewestAdded() {
        Transaction older = repository.add(expense(LocalDate.of(2026, 9, 30), food, 1, ""));
        Transaction newest = repository.add(expense(LocalDate.of(2026, 10, 5), food, 2, ""));
        Transaction sameDayLater = repository.add(expense(LocalDate.of(2026, 10, 5), food, 3, ""));
        Transaction middle = repository.add(expense(LocalDate.of(2026, 10, 1), food, 4, ""));

        assertEquals(List.of(sameDayLater, newest, middle, older), repository.find(TransactionFilter.ALL));
    }

    @Test
    void aMonthFilterIncludesItsFirstAndLastDayOnly() {
        repository.add(expense(LocalDate.of(2026, 1, 31), food, 1, ""));
        Transaction first = repository.add(expense(LocalDate.of(2026, 2, 1), food, 2, ""));
        Transaction last = repository.add(expense(LocalDate.of(2026, 2, 28), food, 3, ""));
        repository.add(expense(LocalDate.of(2026, 3, 1), food, 4, ""));
        repository.add(expense(LocalDate.of(2025, 2, 15), food, 5, ""));

        assertEquals(List.of(last, first), repository.find(TransactionFilter.ALL.inMonth(Month.of(2026, 2))));
    }

    @Test
    void filtersByCategoryAndTypeCombine() {
        Transaction lunch = repository.add(expense(LocalDate.of(2026, 10, 1), food, 1, ""));
        Transaction game = repository.add(expense(LocalDate.of(2026, 10, 2), games, 2, ""));
        Transaction pay = repository.add(new TransactionDetails(
                LocalDate.of(2026, 10, 3), TransactionType.INCOME, salary, Money.ofHalere(3), ""));

        assertEquals(List.of(game), repository.find(TransactionFilter.ALL.inCategory(games)));
        assertEquals(List.of(game, lunch), repository.find(TransactionFilter.ALL.ofType(TransactionType.EXPENSE)));
        assertEquals(List.of(pay), repository.find(TransactionFilter.ALL.ofType(TransactionType.INCOME)));
        assertEquals(List.of(), repository.find(TransactionFilter.ALL.inCategory(food).ofType(TransactionType.INCOME)));
    }

    @Test
    void theNoteSearchIsACaseInsensitiveSubstringMatchAlsoForCzechLetters() {
        Transaction hades = repository.add(expense(LocalDate.of(2026, 10, 8), games, 1, "Steam – Hades II"));
        Transaction bread = repository.add(expense(LocalDate.of(2026, 10, 9), food, 2, "ČERSTVÉ pečivo"));
        repository.add(expense(LocalDate.of(2026, 10, 10), food, 3, ""));

        assertEquals(List.of(hades), repository.find(TransactionFilter.ALL.withNoteContaining("STEAM")));
        assertEquals(List.of(hades), repository.find(TransactionFilter.ALL.withNoteContaining("des i")));
        assertEquals(List.of(bread), repository.find(TransactionFilter.ALL.withNoteContaining("čerstvé PEČ")));
        assertEquals(List.of(), repository.find(TransactionFilter.ALL.withNoteContaining("%")));
    }

    @Test
    void theNoteSearchCombinesWithTheOtherFilters() {
        Transaction october = repository.add(expense(LocalDate.of(2026, 10, 8), games, 1, "Steam"));
        repository.add(expense(LocalDate.of(2026, 9, 8), games, 2, "Steam"));
        repository.add(expense(LocalDate.of(2026, 10, 9), food, 3, "Steam bun"));

        assertEquals(List.of(october), repository.find(TransactionFilter.ALL
                .withNoteContaining("steam").inMonth(Month.of(2026, 10)).inCategory(games)));
    }

    @Test
    void categoryTotalsSumEachCategoryOfTheMonthIncludingRefunds() {
        repository.add(expense(LocalDate.of(2026, 10, 1), food, 30_000, ""));
        repository.add(expense(LocalDate.of(2026, 10, 31), food, -5_050, "Refund"));
        repository.add(expense(LocalDate.of(2026, 10, 8), games, 99_900, ""));
        repository.add(new TransactionDetails(
                LocalDate.of(2026, 10, 10), TransactionType.INCOME, salary, Money.ofHalere(3_000_000), ""));
        repository.add(expense(LocalDate.of(2026, 9, 30), food, 1, ""));
        repository.add(expense(LocalDate.of(2026, 11, 1), games, 2, ""));

        assertEquals(Set.of(
                        new CategoryTotal(food, TransactionType.EXPENSE, Money.ofHalere(24_950)),
                        new CategoryTotal(games, TransactionType.EXPENSE, Money.ofHalere(99_900)),
                        new CategoryTotal(salary, TransactionType.INCOME, Money.ofHalere(3_000_000))),
                Set.copyOf(repository.categoryTotals(Month.of(2026, 10))));
    }

    @Test
    void categoryTotalsOfAMonthWithoutTransactionsAreEmpty() {
        repository.add(expense(LocalDate.of(2026, 10, 1), food, 100, ""));

        assertEquals(List.of(), repository.categoryTotals(Month.of(2026, 8)));
    }

    @Test
    void aMergeMovesEveryTransactionOfOneCategoryToAnotherAndKeepsTheRest() {
        Transaction lunch = repository.add(expense(LocalDate.of(2026, 10, 1), food, 189, "Lunch"));
        Transaction hades = repository.add(expense(LocalDate.of(2025, 3, 8), games, 600, "Steam – Hades II"));
        Transaction sale = repository.add(expense(LocalDate.of(2026, 7, 1), games, -400, "Refund"));

        repository.moveToCategory(games, food);

        assertEquals(List.of(), repository.find(TransactionFilter.ALL.inCategory(games)));
        List<Transaction> moved = repository.find(TransactionFilter.ALL.inCategory(food));
        assertEquals(List.of(lunch.id(), sale.id(), hades.id()), moved.stream().map(Transaction::id).toList());
        assertEquals(List.of("Lunch", "Refund", "Steam – Hades II"), moved.stream().map(Transaction::note).toList());
        assertEquals(Money.ofHalere(-400), moved.get(1).amount());
        assertEquals(LocalDate.of(2025, 3, 8), moved.get(2).date());
    }

    @Test
    void aMergeLeavesOtherCategoriesAlone() {
        Transaction pay = repository.add(new TransactionDetails(
                LocalDate.of(2026, 10, 10), TransactionType.INCOME, salary, Money.ofHalere(3_000_000), ""));
        repository.add(expense(LocalDate.of(2026, 10, 1), games, 600, ""));

        repository.moveToCategory(games, food);

        assertEquals(Optional.of(pay), repository.find(pay.id()));
    }

    private static TransactionDetails expense(LocalDate date, long categoryId, long halere, String note) {
        return new TransactionDetails(date, TransactionType.EXPENSE, categoryId, Money.ofHalere(halere), note);
    }
}
