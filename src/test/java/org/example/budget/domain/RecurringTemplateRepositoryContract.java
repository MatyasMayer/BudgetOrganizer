package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The shared suite every {@link RecurringTemplateRepository} must pass (in-memory fake and SQLite).
 * Categories and Transactions come from the matching repositories, since SQLite enforces the references.
 */
public abstract class RecurringTemplateRepositoryContract {

    private RecurringTemplateRepository repository;
    private TransactionRepository transactions;
    private long rent;
    private long subscriptions;
    private long salary;

    /** Fresh, empty repositories sharing one store. */
    protected abstract Repositories createRepositories() throws Exception;

    public record Repositories(CategoryRepository categories, TransactionRepository transactions,
                               RecurringTemplateRepository recurringTemplates) {
    }

    @BeforeEach
    void setUp() throws Exception {
        Repositories repositories = createRepositories();
        repository = repositories.recurringTemplates();
        transactions = repositories.transactions();
        rent = repositories.categories().add("Rent", TransactionType.EXPENSE, false).id();
        subscriptions = repositories.categories().add("Subscriptions", TransactionType.EXPENSE, false).id();
        salary = repositories.categories().add("Salary", TransactionType.INCOME, false).id();
    }

    @Test
    void aNewRepositoryHasNoTemplates() {
        assertEquals(List.of(), repository.findAll());
        assertEquals(Optional.empty(), repository.find(1));
    }

    @Test
    void anAddedTemplateGetsAnIdAndKeepsEveryField() {
        RecurringTemplateDetails details = new RecurringTemplateDetails(TransactionType.EXPENSE, rent,
                Money.ofHalere(1_200_000), "Flat – Vinohrady", Frequency.MONTHLY, 31,
                LocalDate.of(2026, 1, 31), Optional.of(LocalDate.of(2027, 6, 30)));

        RecurringTemplate template = repository.add(details);

        assertEquals(new RecurringTemplate(template.id(), details), template);
        assertEquals(Optional.of(template), repository.find(template.id()));
    }

    @Test
    void aTemplateWithoutNoteOrEndDateStaysThatWay() {
        RecurringTemplateDetails details = new RecurringTemplateDetails(TransactionType.INCOME, salary,
                Money.ofHalere(3_500_000), "  ", Frequency.YEARLY, 15, LocalDate.of(2026, 3, 15), Optional.empty());

        RecurringTemplate template = repository.add(details);

        assertEquals(Optional.of(template), repository.find(template.id()));
        assertEquals("", repository.find(template.id()).orElseThrow().details().note());
    }

    @Test
    void allTemplatesAreListedInTheOrderTheyWereAdded() {
        RecurringTemplate first = repository.add(spotify(subscriptions));
        RecurringTemplate second = repository.add(spotify(rent));

        assertNotEquals(first.id(), second.id());
        assertEquals(List.of(first, second), repository.findAll());
    }

    @Test
    void movingToAnotherCategoryMovesOnlyThatCategorysTemplates() {
        RecurringTemplate spotify = repository.add(spotify(subscriptions));
        RecurringTemplate flat = repository.add(spotify(rent));

        repository.moveToCategory(subscriptions, rent);

        assertEquals(rent, repository.find(spotify.id()).orElseThrow().details().categoryId());
        assertEquals(flat, repository.find(flat.id()).orElseThrow());
    }

    @Test
    void anUpdateReplacesEveryFieldOfOnlyThatTemplateAndRemembersTheDayOfTheEdit() {
        RecurringTemplate spotify = repository.add(spotify(subscriptions));
        RecurringTemplate flat = repository.add(spotify(rent));
        RecurringTemplateDetails changed = new RecurringTemplateDetails(TransactionType.INCOME, salary,
                Money.ofHalere(19_900), "  ", Frequency.YEARLY, 31, LocalDate.of(2026, 10, 1),
                Optional.of(LocalDate.of(2027, 1, 31)));

        repository.update(new RecurringTemplate(spotify.id(), changed), LocalDate.of(2026, 10, 17));

        assertEquals(Optional.of(new RecurringTemplate(spotify.id(), changed)), repository.find(spotify.id()));
        assertEquals(Optional.of(LocalDate.of(2026, 10, 17)), repository.lastEdited(spotify.id()));
        assertEquals(Optional.of(flat), repository.find(flat.id()));
        assertEquals(Optional.empty(), repository.lastEdited(flat.id()));
    }

    @Test
    void theLatestEditIsRemembered() {
        RecurringTemplate spotify = repository.add(spotify(subscriptions));

        repository.update(spotify, LocalDate.of(2026, 10, 17));
        repository.update(spotify, LocalDate.of(2026, 12, 1));

        assertEquals(Optional.of(LocalDate.of(2026, 12, 1)), repository.lastEdited(spotify.id()));
    }

    @Test
    void theLastOccurrenceIsTheLatestGeneratedDateOfThatTemplate() {
        RecurringTemplate spotify = repository.add(spotify(subscriptions));
        RecurringTemplate other = repository.add(spotify(rent));
        assertEquals(Optional.empty(), repository.lastOccurrence(spotify.id()));

        repository.addOccurrence(spotify.id(), LocalDate.of(2026, 10, 5), generated(LocalDate.of(2026, 10, 5)));
        repository.addOccurrence(spotify.id(), LocalDate.of(2026, 9, 5), generated(LocalDate.of(2026, 9, 5)));
        repository.addOccurrence(other.id(), LocalDate.of(2026, 12, 1), generated(LocalDate.of(2026, 12, 1)));

        assertEquals(Optional.of(LocalDate.of(2026, 10, 5)), repository.lastOccurrence(spotify.id()));
    }

    @Test
    void anOccurrenceCannotBeRecordedTwice() {
        RecurringTemplate spotify = repository.add(spotify(subscriptions));
        LocalDate date = LocalDate.of(2026, 10, 5);
        repository.addOccurrence(spotify.id(), date, generated(date));

        assertThrows(RuntimeException.class, () -> repository.addOccurrence(spotify.id(), date, generated(date)));
    }

    @Test
    void generatedTransactionsAreKnown() {
        RecurringTemplate spotify = repository.add(spotify(subscriptions));
        long september = generated(LocalDate.of(2026, 9, 5));
        long october = generated(LocalDate.of(2026, 10, 5));
        generated(LocalDate.of(2026, 10, 6));

        repository.addOccurrence(spotify.id(), LocalDate.of(2026, 9, 5), september);
        repository.addOccurrence(spotify.id(), LocalDate.of(2026, 10, 5), october);

        assertEquals(Set.of(september, october), repository.generatedTransactionIds());
    }

    @Test
    void anOccurrenceIsRememberedAfterItsTransactionIsDeleted() {
        RecurringTemplate spotify = repository.add(spotify(subscriptions));
        LocalDate date = LocalDate.of(2026, 10, 5);
        long transaction = generated(date);
        repository.addOccurrence(spotify.id(), date, transaction);

        transactions.delete(transaction);

        assertEquals(Optional.empty(), transactions.find(transaction));
        assertEquals(Optional.of(date), repository.lastOccurrence(spotify.id()));
    }

    private long generated(LocalDate date) {
        return transactions.add(new TransactionDetails(date, TransactionType.EXPENSE, subscriptions,
                Money.ofHalere(16_900), "Spotify")).id();
    }

    private static RecurringTemplateDetails spotify(long categoryId) {
        return new RecurringTemplateDetails(TransactionType.EXPENSE, categoryId, Money.ofHalere(16_900), "Spotify",
                Frequency.MONTHLY, 5, LocalDate.of(2026, 9, 1), Optional.empty());
    }
}
