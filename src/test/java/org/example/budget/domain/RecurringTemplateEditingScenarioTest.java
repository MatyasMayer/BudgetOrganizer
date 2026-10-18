package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Facade-level scenarios for editing and ending Recurring Templates and for changing single occurrences. */
class RecurringTemplateEditingScenarioTest {

    private final AtomicReference<LocalDate> today = new AtomicReference<>(LocalDate.of(2026, 10, 17));

    private final BudgetService service = new BudgetService(
            new InMemorySettingsRepository(), new InMemoryCategoryRepository(), new InMemoryTransactionRepository(),
            new InMemoryRecurringTemplateRepository(), new InMemoryBudgetLimitRepository(), today::get);

    private long subscriptions;

    @BeforeEach
    void seed() {
        service.seedPresetCategories(PresetCategory::name);
        subscriptions = category(TransactionType.EXPENSE, "SUBSCRIPTIONS");
    }

    @Test
    void aPriceIncreaseAppliesOnlyToOccurrencesGeneratedAfterTheEdit() {
        RecurringTemplate spotify = service.createRecurringTemplate(spotify(16_900, 5, LocalDate.of(2026, 9, 1)));
        service.generateDueOccurrences();

        service.editRecurringTemplate(spotify.id(), spotify(19_900, 5, LocalDate.of(2026, 9, 1)));
        service.generateDueOccurrences();
        today.set(LocalDate.of(2026, 12, 5));
        service.generateDueOccurrences();

        assertEquals(List.of(
                        occurrence(LocalDate.of(2026, 9, 5), 16_900), occurrence(LocalDate.of(2026, 10, 5), 16_900),
                        occurrence(LocalDate.of(2026, 11, 5), 19_900), occurrence(LocalDate.of(2026, 12, 5), 19_900)),
                spotifyTransactions());
        assertEquals(spotify(19_900, 5, LocalDate.of(2026, 9, 1)),
                service.recurringTemplates().getFirst().details());
    }

    @Test
    void movingTheDayLaterDoesNotChargeAMonthThatAlreadyHasItsOccurrence() {
        RecurringTemplate spotify = service.createRecurringTemplate(spotify(16_900, 5, LocalDate.of(2026, 9, 1)));
        service.generateDueOccurrences();

        service.editRecurringTemplate(spotify.id(), spotify(16_900, 25, LocalDate.of(2026, 9, 1)));
        today.set(LocalDate.of(2026, 11, 30));
        service.generateDueOccurrences();

        assertEquals(List.of(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 25)),
                spotifyDates());
    }

    @Test
    void movingTheDayEarlierStillChargesTheCurrentMonth() {
        RecurringTemplate spotify = service.createRecurringTemplate(spotify(16_900, 20, LocalDate.of(2026, 9, 1)));
        service.generateDueOccurrences();

        service.editRecurringTemplate(spotify.id(), spotify(16_900, 10, LocalDate.of(2026, 9, 1)));
        service.generateDueOccurrences();

        assertEquals(List.of(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 10)), spotifyDates());
    }

    @Test
    void anEditNeverFillsInMonthsBeforeIt() {
        RecurringTemplate domain = service.createRecurringTemplate(new RecurringTemplateDetails(
                TransactionType.EXPENSE, subscriptions, Money.ofHalere(99_900), "Spotify", Frequency.YEARLY, 15,
                LocalDate.of(2026, 3, 1), Optional.empty()));
        service.generateDueOccurrences();

        service.editRecurringTemplate(domain.id(), spotify(16_900, 15, LocalDate.of(2025, 1, 1)));
        service.generateDueOccurrences();

        assertEquals(List.of(LocalDate.of(2026, 3, 15), LocalDate.of(2026, 10, 15)), spotifyDates());
    }

    @Test
    void anEditedTemplateKeepsGeneratingAfterTheAppIsReopened() {
        RecurringTemplate spotify = service.createRecurringTemplate(spotify(16_900, 5, LocalDate.of(2026, 9, 1)));
        service.generateDueOccurrences();
        service.editRecurringTemplate(spotify.id(), spotify(19_900, 5, LocalDate.of(2026, 9, 1)));

        today.set(LocalDate.of(2027, 1, 6));
        service.generateDueOccurrences();

        assertEquals(List.of(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 5),
                LocalDate.of(2026, 12, 5), LocalDate.of(2027, 1, 5)), spotifyDates());
    }

    @Test
    void anEndedTemplateStopsGeneratingButStaysListedAsEndedWithItsHistory() {
        RecurringTemplate spotify = service.createRecurringTemplate(spotify(16_900, 5, LocalDate.of(2026, 9, 1)));
        service.generateDueOccurrences();
        Set<Long> generatedBefore = service.generatedTransactionIds();

        service.endRecurringTemplate(spotify.id(), LocalDate.of(2026, 10, 31));
        today.set(LocalDate.of(2027, 2, 1));
        service.generateDueOccurrences();

        RecurringTemplate ended = service.recurringTemplates().getFirst();
        assertEquals(spotify.id(), ended.id());
        assertEquals(Optional.of(LocalDate.of(2026, 10, 31)), ended.details().endDate());
        assertTrue(ended.details().hasEndedBy(today.get()));
        assertEquals(List.of(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 5)), spotifyDates());
        assertEquals(generatedBefore, service.generatedTransactionIds());
    }

    @Test
    void aTemplateHasEndedOnlyAfterItsEndDate() {
        RecurringTemplateDetails details = new RecurringTemplateDetails(TransactionType.EXPENSE, subscriptions,
                Money.ofHalere(16_900), "Spotify", Frequency.MONTHLY, 5, LocalDate.of(2026, 9, 1),
                Optional.of(LocalDate.of(2026, 10, 31)));

        assertFalse(details.hasEndedBy(LocalDate.of(2026, 10, 31)));
        assertTrue(details.hasEndedBy(LocalDate.of(2026, 11, 1)));
        assertFalse(spotify(16_900, 5, LocalDate.of(2026, 9, 1)).hasEndedBy(LocalDate.of(2099, 1, 1)));
    }

    @Test
    void anEndDateBeforeTheStartIsRefusedAndAnUnknownTemplateCannotBeChanged() {
        RecurringTemplate spotify = service.createRecurringTemplate(spotify(16_900, 5, LocalDate.of(2026, 9, 1)));

        InvalidRecurringTemplateException beforeStart = assertThrows(InvalidRecurringTemplateException.class,
                () -> service.endRecurringTemplate(spotify.id(), LocalDate.of(2026, 8, 31)));
        InvalidRecurringTemplateException unknownEnd = assertThrows(InvalidRecurringTemplateException.class,
                () -> service.endRecurringTemplate(9_999, LocalDate.of(2026, 12, 31)));
        InvalidRecurringTemplateException unknownEdit = assertThrows(InvalidRecurringTemplateException.class,
                () -> service.editRecurringTemplate(9_999, spotify(19_900, 5, LocalDate.of(2026, 9, 1))));

        assertEquals(InvalidRecurringTemplateException.Problem.END_BEFORE_START, beforeStart.problem());
        assertEquals(InvalidRecurringTemplateException.Problem.UNKNOWN_TEMPLATE, unknownEnd.problem());
        assertEquals(InvalidRecurringTemplateException.Problem.UNKNOWN_TEMPLATE, unknownEdit.problem());
        assertEquals(List.of(spotify), service.recurringTemplates());
    }

    @Test
    void anInvalidEditLeavesTheTemplateAsItWas() {
        RecurringTemplate spotify = service.createRecurringTemplate(spotify(16_900, 5, LocalDate.of(2026, 9, 1)));

        assertThrows(InvalidRecurringTemplateException.class,
                () -> service.editRecurringTemplate(spotify.id(), spotify(0, 5, LocalDate.of(2026, 9, 1))));

        assertEquals(List.of(spotify), service.recurringTemplates());
    }

    @Test
    void editingOneOccurrenceLeavesTheTemplateAndOtherOccurrencesUntouched() {
        RecurringTemplate spotify = service.createRecurringTemplate(spotify(16_900, 5, LocalDate.of(2026, 9, 1)));
        service.generateDueOccurrences();
        Transaction october = service.transactions(TransactionFilter.ALL.inMonth(Month.of(2026, 10))).getFirst();

        service.editTransaction(october.id(), new TransactionDetails(LocalDate.of(2026, 10, 7),
                TransactionType.EXPENSE, subscriptions, Money.ofHalere(25_000), "Spotify family trial"));
        service.generateDueOccurrences();
        today.set(LocalDate.of(2026, 11, 5));
        service.generateDueOccurrences();

        assertEquals(List.of(occurrence(LocalDate.of(2026, 9, 5), 16_900),
                new TransactionDetails(LocalDate.of(2026, 10, 7), TransactionType.EXPENSE, subscriptions,
                        Money.ofHalere(25_000), "Spotify family trial"),
                occurrence(LocalDate.of(2026, 11, 5), 16_900)), spotifyTransactions());
        assertEquals(List.of(spotify), service.recurringTemplates());
        assertTrue(service.generatedTransactionIds().contains(october.id()));
    }

    @Test
    void deletingOneOccurrenceLeavesTheTemplateAndOtherOccurrencesUntouchedAndItIsNotRegenerated() {
        RecurringTemplate spotify = service.createRecurringTemplate(spotify(16_900, 5, LocalDate.of(2026, 9, 1)));
        service.generateDueOccurrences();
        Transaction september = service.transactions(TransactionFilter.ALL.inMonth(Month.of(2026, 9))).getFirst();

        service.deleteTransaction(september.id());
        service.editRecurringTemplate(spotify.id(), spotify(19_900, 5, LocalDate.of(2026, 9, 1)));
        today.set(LocalDate.of(2026, 11, 5));
        service.generateDueOccurrences();

        assertEquals(List.of(occurrence(LocalDate.of(2026, 10, 5), 16_900),
                occurrence(LocalDate.of(2026, 11, 5), 19_900)), spotifyTransactions());
        assertEquals(spotify.id(), service.recurringTemplates().getFirst().id());
    }

    private List<LocalDate> spotifyDates() {
        return spotifyTransactions().stream().map(TransactionDetails::date).toList();
    }

    private RecurringTemplateDetails spotify(long halere, int dayOfMonth, LocalDate start) {
        return new RecurringTemplateDetails(TransactionType.EXPENSE, subscriptions, Money.ofHalere(halere), "Spotify",
                Frequency.MONTHLY, dayOfMonth, start, Optional.empty());
    }

    private TransactionDetails occurrence(LocalDate date, long halere) {
        return new TransactionDetails(date, TransactionType.EXPENSE, subscriptions, Money.ofHalere(halere), "Spotify");
    }

    /** The Transactions in Subscriptions, oldest first. */
    private List<TransactionDetails> spotifyTransactions() {
        return service.transactions(TransactionFilter.ALL.inCategory(subscriptions)).stream()
                .map(Transaction::details).toList().reversed();
    }

    private long category(TransactionType type, String name) {
        return service.categories(type).stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow().id();
    }
}
