package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Facade-level scenarios for Recurring Templates in EUR: each occurrence converted at its due date's ČNB Rate. */
class EurRecurringTemplateScenarioTest {

    private final AtomicReference<LocalDate> today = new AtomicReference<>(LocalDate.of(2026, 10, 17));
    private final FakeRateSource cnb = new FakeRateSource()
            .publish(Currency.EUR, LocalDate.of(2026, 8, 5), "24.500")
            .publish(Currency.EUR, LocalDate.of(2026, 9, 4), "24.400")
            .publish(Currency.EUR, LocalDate.of(2026, 10, 5), "24.325");
    private final InMemoryTransactionRepository transactions = new InMemoryTransactionRepository();
    private final BudgetService service = new BudgetService(
            new InMemorySettingsRepository(), new InMemoryCategoryRepository(), transactions,
            new InMemoryRecurringTemplateRepository(), new InMemoryBudgetLimitRepository(),
            new InMemoryCnbRateRepository(), cnb, today::get);

    private long subscriptions;

    @BeforeEach
    void seed() {
        service.seedPresetCategories(PresetCategory::name);
        subscriptions = service.categories(TransactionType.EXPENSE).stream()
                .filter(c -> c.name().equals("SUBSCRIPTIONS")).findFirst().orElseThrow().id();
    }

    @Test
    void aTemplateCanBeInEur() {
        RecurringTemplate created = service.createRecurringTemplate(spotifyFrom(LocalDate.of(2026, 11, 1)));

        assertEquals(Currency.EUR, created.details().currency());
        assertEquals(Optional.of(eur(1_099)), created.details().originalAmount());
        assertEquals(List.of(created), service.recurringTemplates());
    }

    @Test
    void eachOccurrenceIsConvertedAtTheCnbRateOfItsOwnDueDate() {
        service.createRecurringTemplate(spotifyFrom(LocalDate.of(2026, 8, 1)));

        service.generateDueOccurrences();

        List<Transaction> generated = service.transactions(TransactionFilter.ALL);
        // 10.99 EUR × 24.325 = 267.33; × 24.400 (Friday 4 Sep for Saturday 5 Sep) = 268.16; × 24.500 = 269.26
        assertEquals(List.of(
                spotify(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5), "24.325", 26_733),
                spotify(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 4), "24.400", 26_816),
                spotify(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 5), "24.500", 26_926)),
                generated.stream().map(Transaction::details).toList());
    }

    @Test
    void offlineAnOccurrenceUsesAProvisionalRateThatTheStartupRefreshCorrects() {
        service.createRecurringTemplate(spotifyFrom(LocalDate.of(2026, 9, 1)));
        service.generateDueOccurrences();
        today.set(LocalDate.of(2026, 11, 5));
        cnb.publish(Currency.EUR, LocalDate.of(2026, 11, 5), "24.200");
        cnb.goOffline();

        service.generateDueOccurrences();

        Transaction november = service.transactions(TransactionFilter.ALL).getFirst();
        Conversion provisional = november.details().conversion().orElseThrow();
        assertTrue(provisional.provisionalRate());
        // The most recent cached rate (5 Oct): 10.99 EUR × 24.325 = 267.33 CZK
        assertEquals(LocalDate.of(2026, 10, 5), provisional.cnbRate().validFor());
        assertEquals(Money.ofHalere(26_733), november.amount());

        cnb.goOnline();
        assertEquals(1, service.refreshProvisionalRates());

        assertEquals(spotify(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5), "24.200", 26_596),
                service.transactions(TransactionFilter.ALL).getFirst().details());
    }

    @Test
    void withNoRateAtAllTheOccurrenceWaitsForALaterGeneration() {
        cnb.goOffline();
        service.createRecurringTemplate(spotifyFrom(LocalDate.of(2026, 9, 1)));

        service.generateDueOccurrences();
        assertEquals(List.of(), service.transactions(TransactionFilter.ALL));

        cnb.goOnline();
        service.generateDueOccurrences();

        assertEquals(List.of(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 9, 5)),
                service.transactions(TransactionFilter.ALL).stream().map(Transaction::date).toList());
    }

    @Test
    void aEurTemplateNeedsAPositiveOriginalAmount() {
        assertThrows(InvalidRecurringTemplateException.class, () -> service.createRecurringTemplate(
                RecurringTemplateDetails.inForeignCurrency(TransactionType.EXPENSE, subscriptions, eur(0),
                        "Spotify", Frequency.MONTHLY, 5, LocalDate.of(2026, 9, 1), Optional.empty())));
    }

    private TransactionDetails spotify(LocalDate date, LocalDate validFor, String rate, long halere) {
        TransactionDetails details = TransactionDetails.converted(date, TransactionType.EXPENSE, subscriptions,
                new Conversion(eur(1_099), new CnbRate(Currency.EUR, validFor, new java.math.BigDecimal(rate)),
                        Optional.empty()), "Spotify");
        assertEquals(Money.ofHalere(halere), details.amount());
        return details;
    }

    private RecurringTemplateDetails spotifyFrom(LocalDate start) {
        return RecurringTemplateDetails.inForeignCurrency(TransactionType.EXPENSE, subscriptions, eur(1_099),
                "Spotify", Frequency.MONTHLY, 5, start, Optional.empty());
    }

    private static OriginalAmount eur(long cents) {
        return new OriginalAmount(Currency.EUR, cents);
    }
}
