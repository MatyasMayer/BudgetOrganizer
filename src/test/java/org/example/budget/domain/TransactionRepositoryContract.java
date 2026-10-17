package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void aEurTransactionKeepsItsOriginalAmountAndCnbRate() {
        Conversion conversion = new Conversion(new OriginalAmount(Currency.EUR, 1_999),
                new CnbRate(Currency.EUR, LocalDate.of(2026, 10, 16), new BigDecimal("24.325")), Optional.empty());
        Transaction game = repository.add(TransactionDetails.converted(
                LocalDate.of(2026, 10, 18), TransactionType.EXPENSE, games, conversion, "Steam – Hades II"));

        Transaction found = repository.find(game.id()).orElseThrow();
        assertEquals(game, found);
        assertEquals(Optional.of(conversion), found.details().conversion());
        assertEquals(Money.ofHalere(48_626), found.amount());
    }

    @Test
    void aEurTransactionKeepsItsChargedAmount() {
        Conversion conversion = new Conversion(new OriginalAmount(Currency.EUR, -500),
                new CnbRate(Currency.EUR, LocalDate.of(2026, 10, 16), new BigDecimal("24.325")),
                Optional.of(Money.ofHalere(-12_400)));
        Transaction refund = repository.add(TransactionDetails.converted(
                LocalDate.of(2026, 10, 16), TransactionType.EXPENSE, games, conversion, ""));

        assertEquals(Optional.of(refund), repository.find(refund.id()));
        assertEquals(List.of(refund), repository.find(TransactionFilter.ALL.inCategory(games)));
    }

    @Test
    void aEurTransactionKeepsWhetherItUsesAProvisionalRate() {
        CnbRate rate = new CnbRate(Currency.EUR, LocalDate.of(2026, 10, 16), new BigDecimal("24.325"));
        Conversion provisional = Conversion.provisional(new OriginalAmount(Currency.EUR, 1_999), rate, Optional.empty());
        Transaction game = repository.add(TransactionDetails.converted(
                LocalDate.of(2026, 10, 19), TransactionType.EXPENSE, games, provisional, ""));

        assertTrue(repository.find(game.id()).orElseThrow().details().conversion().orElseThrow().provisionalRate());

        Conversion replaced = new Conversion(new OriginalAmount(Currency.EUR, 1_999),
                new CnbRate(Currency.EUR, LocalDate.of(2026, 10, 19), new BigDecimal("24.3")), Optional.empty());
        Transaction refreshed = new Transaction(game.id(), TransactionDetails.converted(
                LocalDate.of(2026, 10, 19), TransactionType.EXPENSE, games, replaced, ""));
        repository.update(refreshed);

        assertEquals(Optional.of(refreshed), repository.find(game.id()));
        assertFalse(repository.find(game.id()).orElseThrow().details().conversion().orElseThrow().provisionalRate());
    }

    @Test
    void anUpdateCanTurnAEurTransactionIntoACzkOne() {
        Conversion conversion = new Conversion(new OriginalAmount(Currency.EUR, 1_000),
                new CnbRate(Currency.EUR, LocalDate.of(2026, 10, 16), new BigDecimal("24.325")),
                Optional.of(Money.ofHalere(24_500)));
        Transaction original = repository.add(TransactionDetails.converted(
                LocalDate.of(2026, 10, 16), TransactionType.EXPENSE, games, conversion, ""));

        Transaction inCzk = new Transaction(original.id(), expense(LocalDate.of(2026, 10, 16), games, 25_000, ""));
        repository.update(inCzk);

        assertEquals(Optional.of(inCzk), repository.find(original.id()));
        assertEquals(Optional.empty(), repository.find(original.id()).orElseThrow().details().conversion());
    }

    @Test
    void categoryTotalsCountEurTransactionsWithTheirEffectiveCzkAmount() {
        CnbRate rate = new CnbRate(Currency.EUR, LocalDate.of(2026, 10, 16), new BigDecimal("24.325"));
        repository.add(TransactionDetails.converted(LocalDate.of(2026, 10, 16), TransactionType.EXPENSE, games,
                new Conversion(new OriginalAmount(Currency.EUR, 1_999), rate, Optional.empty()), ""));
        repository.add(TransactionDetails.converted(LocalDate.of(2026, 10, 17), TransactionType.EXPENSE, games,
                new Conversion(new OriginalAmount(Currency.EUR, 1_000), rate, Optional.of(Money.ofHalere(25_000))),
                ""));
        repository.add(expense(LocalDate.of(2026, 10, 18), games, 10_000, ""));

        // 486.26 (19.99 EUR at 24.325) + 250.00 (Charged Amount, not 243.25) + 100.00 CZK
        assertEquals(List.of(new CategoryTotal(games, TransactionType.EXPENSE, Money.ofHalere(83_626))),
                repository.categoryTotals(Month.of(2026, 10)));
    }

    @Test
    void aMergeKeepsTheConversionOfEurTransactions() {
        Conversion conversion = new Conversion(new OriginalAmount(Currency.EUR, 1_999),
                new CnbRate(Currency.EUR, LocalDate.of(2026, 10, 16), new BigDecimal("24.325")),
                Optional.of(Money.ofHalere(49_310)));
        Transaction hades = repository.add(TransactionDetails.converted(
                LocalDate.of(2026, 10, 16), TransactionType.EXPENSE, games, conversion, "Steam"));

        repository.moveToCategory(games, food);

        Transaction moved = repository.find(hades.id()).orElseThrow();
        assertEquals(food, moved.categoryId());
        assertEquals(Optional.of(conversion), moved.details().conversion());
        assertEquals(Money.ofHalere(49_310), moved.amount());
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
    void categoryTotalsBetweenTwoDatesIncludeBothEndDays() {
        repository.add(expense(LocalDate.of(2026, 9, 30), food, 1, ""));
        repository.add(expense(LocalDate.of(2026, 10, 1), food, 30_000, ""));
        repository.add(expense(LocalDate.of(2026, 10, 10), food, -5_050, "Refund"));
        repository.add(expense(LocalDate.of(2026, 10, 10), games, 99_900, ""));
        repository.add(expense(LocalDate.of(2026, 10, 11), games, 2, ""));
        repository.add(new TransactionDetails(
                LocalDate.of(2026, 10, 5), TransactionType.INCOME, salary, Money.ofHalere(3_000_000), ""));

        assertEquals(Set.of(
                        new CategoryTotal(food, TransactionType.EXPENSE, Money.ofHalere(24_950)),
                        new CategoryTotal(games, TransactionType.EXPENSE, Money.ofHalere(99_900)),
                        new CategoryTotal(salary, TransactionType.INCOME, Money.ofHalere(3_000_000))),
                Set.copyOf(repository.categoryTotals(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10))));
    }

    @Test
    void monthTotalsSumIncomeAndExpensesPerMonthOldestFirstIncludingRefunds() {
        repository.add(expense(LocalDate.of(2026, 10, 31), food, 30_000, ""));
        repository.add(expense(LocalDate.of(2026, 10, 1), games, -5_050, "Refund"));
        repository.add(new TransactionDetails(
                LocalDate.of(2026, 10, 10), TransactionType.INCOME, salary, Money.ofHalere(3_000_000), ""));
        repository.add(expense(LocalDate.of(2025, 12, 31), food, 1, ""));
        repository.add(new TransactionDetails(
                LocalDate.of(2026, 2, 1), TransactionType.INCOME, salary, Money.ofHalere(7), ""));

        assertEquals(List.of(
                        new MonthTotals(Month.of(2025, 12), Money.ZERO, Money.ofHalere(1)),
                        new MonthTotals(Month.of(2026, 2), Money.ofHalere(7), Money.ZERO),
                        new MonthTotals(Month.of(2026, 10), Money.ofHalere(3_000_000), Money.ofHalere(24_950))),
                repository.monthTotals());
    }

    @Test
    void monthTotalsOfAnEmptyRepositoryAreEmpty() {
        assertEquals(List.of(), repository.monthTotals());
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

    @Test
    void aSavingsFundedExpenseKeepsItsMarkThroughAddUpdateAndMerge() {
        Transaction pc = repository.add(expense(LocalDate.of(2026, 10, 12), games, 4_000_000, "PC")
                .withSavingsFunded(true));
        assertTrue(repository.find(pc.id()).orElseThrow().details().savingsFunded());

        repository.moveToCategory(games, food);
        assertTrue(repository.find(pc.id()).orElseThrow().details().savingsFunded());

        repository.update(new Transaction(pc.id(), pc.details().withSavingsFunded(false)));
        assertFalse(repository.find(pc.id()).orElseThrow().details().savingsFunded());
    }

    @Test
    void categoryAndMonthTotalsLeaveSavingsFundedExpensesOut() {
        repository.add(expense(LocalDate.of(2026, 10, 3), food, 50_000, ""));
        repository.add(expense(LocalDate.of(2026, 10, 12), food, 300_000, "").withSavingsFunded(true));
        repository.add(expense(LocalDate.of(2026, 10, 12), games, 4_000_000, "PC").withSavingsFunded(true));

        List<CategoryTotal> expected = List.of(new CategoryTotal(food, TransactionType.EXPENSE, Money.ofHalere(50_000)));
        assertEquals(expected, repository.categoryTotals(Month.of(2026, 10)));
        assertEquals(expected, repository.categoryTotals(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
        assertEquals(List.of(new MonthTotals(Month.of(2026, 10), Money.ZERO, Money.ofHalere(50_000))),
                repository.monthTotals());
    }

    @Test
    void savingsFundedTotalsSumOnlySavingsFundedExpensesPerCategoryOfTheMonth() {
        repository.add(expense(LocalDate.of(2026, 10, 3), food, 50_000, ""));
        repository.add(expense(LocalDate.of(2026, 10, 12), food, 300_000, "").withSavingsFunded(true));
        repository.add(expense(LocalDate.of(2026, 10, 13), food, 1, "").withSavingsFunded(true));
        repository.add(expense(LocalDate.of(2026, 9, 30), games, 4_000_000, "PC").withSavingsFunded(true));

        assertEquals(List.of(new CategoryTotal(food, TransactionType.EXPENSE, Money.ofHalere(300_001))),
                repository.savingsFundedTotals(Month.of(2026, 10)));
    }

    @Test
    void theSavingsFundedTotalSumsEverySavingsFundedExpenseEver() {
        assertEquals(Money.ZERO, repository.savingsFundedTotal());
        repository.add(expense(LocalDate.of(2026, 10, 3), food, 50_000, ""));
        repository.add(expense(LocalDate.of(2026, 10, 12), food, 300_000, "").withSavingsFunded(true));
        repository.add(expense(LocalDate.of(2025, 1, 30), games, 4_000_000, "PC").withSavingsFunded(true));

        assertEquals(Money.ofHalere(4_300_000), repository.savingsFundedTotal());
    }

    private static TransactionDetails expense(LocalDate date, long categoryId, long halere, String note) {
        return new TransactionDetails(date, TransactionType.EXPENSE, categoryId, Money.ofHalere(halere), note);
    }
}
