package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Facade-level scenarios for EUR Transactions entered while ČNB can't be reached (the fake's offline mode). */
class ProvisionalRateScenarioTest {

    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 16);
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 10, 20);

    private final FakeRateSource cnb = new FakeRateSource()
            .publish(Currency.EUR, LocalDate.of(2026, 10, 15), "24.410")
            .publish(Currency.EUR, FRIDAY, "24.325");
    private final InMemoryCnbRateRepository rateCache = new InMemoryCnbRateRepository();
    private final InMemoryCategoryRepository categories = new InMemoryCategoryRepository();
    private final InMemoryTransactionRepository transactions = new InMemoryTransactionRepository();
    private final BudgetService service = serviceOn(TUESDAY);

    private long games;

    @BeforeEach
    void seed() {
        service.seedPresetCategories(PresetCategory::name);
        games = service.categories(TransactionType.EXPENSE).stream()
                .filter(c -> c.name().equals("GAMES")).findFirst().orElseThrow().id();
    }

    @Test
    void offlineAEurTransactionIsSavedWithTheMostRecentCachedRateAsAProvisionalRate() {
        service.cnbRate(Currency.EUR, LocalDate.of(2026, 10, 15));
        service.cnbRate(Currency.EUR, FRIDAY);
        cnb.goOffline();

        Transaction hades = record(MONDAY, convert(eur(1_999), MONDAY, Optional.empty()));

        Conversion conversion = hades.details().conversion().orElseThrow();
        assertTrue(conversion.provisionalRate());
        assertEquals(FRIDAY, conversion.cnbRate().validFor());
        // 19.99 EUR × 24.325 = 486.26 CZK
        assertEquals(halere(48_626), hades.amount());
        assertEquals(halere(48_626), service.monthSummary(Month.of(2026, 10)).expenses());
    }

    @Test
    void theRateToUseTellsWhetherItIsProvisional() {
        assertEquals(Optional.of(new AvailableRate(fridayRate(), false)), service.rateToUse(Currency.EUR, FRIDAY));

        cnb.goOffline();

        assertEquals(Optional.of(new AvailableRate(fridayRate(), true)), service.rateToUse(Currency.EUR, MONDAY));
    }

    @Test
    void withCnbReachableTheRateIsNotProvisional() {
        Transaction hades = record(MONDAY, convert(eur(1_999), MONDAY, Optional.empty()));

        assertFalse(hades.details().conversion().orElseThrow().provisionalRate());
    }

    @Test
    void withNoCachedRateAtAllThereIsNothingToConvertWith() {
        cnb.goOffline();

        assertEquals(Optional.empty(), service.rateToUse(Currency.EUR, MONDAY));
        assertEquals(Optional.empty(), service.convert(eur(1_999), MONDAY, Optional.empty()));
    }

    @Test
    void withNoCachedRateTheUserCanEnterTheRate() {
        cnb.goOffline();

        Conversion conversion = service.convertAtRate(eur(1_999), MONDAY, new BigDecimal("24.5"), Optional.empty());
        Transaction hades = record(MONDAY, conversion);

        assertTrue(conversion.provisionalRate());
        // 19.99 EUR × 24.5 = 489.755 → 489.76 CZK
        assertEquals(halere(48_976), hades.amount());
    }

    @Test
    void withNoCachedRateAChargedAmountIsEnough() {
        cnb.goOffline();

        Transaction hades = record(MONDAY, convert(eur(1_999), MONDAY, Optional.of(halere(49_310))));

        assertEquals(halere(49_310), hades.amount());
        assertTrue(hades.details().conversion().orElseThrow().provisionalRate());
    }

    @Test
    void onRefreshProvisionalRatesAreReplacedAndTheCzkAmountRecalculated() {
        service.cnbRate(Currency.EUR, LocalDate.of(2026, 10, 15));
        cnb.goOffline();
        Transaction hades = record(FRIDAY, convert(eur(1_999), FRIDAY, Optional.empty()));
        // 19.99 EUR at Thursday's 24.41 = 487.9559 → 487.96 CZK
        assertEquals(halere(48_796), hades.amount());

        BudgetService online = restartOnline();
        assertEquals(1, online.refreshProvisionalRates());

        Transaction refreshed = online.transactions(TransactionFilter.ALL).getFirst();
        Conversion conversion = refreshed.details().conversion().orElseThrow();
        assertFalse(conversion.provisionalRate());
        assertEquals(fridayRate(), conversion.cnbRate());
        assertEquals(halere(48_626), refreshed.amount());
        assertEquals(halere(48_626), online.monthSummary(Month.of(2026, 10)).expenses());
        assertEquals("Steam", refreshed.note());
    }

    @Test
    void aRateTheUserEnteredIsReplacedOnRefreshToo() {
        cnb.goOffline();
        record(FRIDAY, service.convertAtRate(eur(1_000), FRIDAY, new BigDecimal("30"), Optional.empty()));

        restartOnline().refreshProvisionalRates();

        assertEquals(halere(24_325), service.transactions(TransactionFilter.ALL).getFirst().amount());
    }

    @Test
    void aTransactionWithAChargedAmountIsNeverRecalculated() {
        service.cnbRate(Currency.EUR, LocalDate.of(2026, 10, 15));
        cnb.goOffline();
        Transaction charged = record(FRIDAY, convert(eur(1_999), FRIDAY, Optional.of(halere(49_310))));

        assertEquals(0, restartOnline().refreshProvisionalRates());

        assertEquals(charged, service.transactions(TransactionFilter.ALL).getFirst());
        assertEquals(halere(49_310), service.monthSummary(Month.of(2026, 10)).expenses());
    }

    @Test
    void whileStillOfflineProvisionalRatesStay() {
        service.cnbRate(Currency.EUR, LocalDate.of(2026, 10, 15));
        cnb.goOffline();
        Transaction hades = record(FRIDAY, convert(eur(1_999), FRIDAY, Optional.empty()));

        assertEquals(0, serviceOn(TUESDAY).refreshProvisionalRates());

        assertEquals(hades, service.transactions(TransactionFilter.ALL).getFirst());
    }

    @Test
    void refreshingLeavesTransactionsWithTheirCnbRateAlone() {
        record(FRIDAY, convert(eur(1_999), FRIDAY, Optional.empty()));
        int requests = cnb.requests();

        assertEquals(0, service.refreshProvisionalRates());

        assertEquals(requests, cnb.requests());
    }

    private BudgetService restartOnline() {
        FakeRateSource onlineCnb = new FakeRateSource()
                .publish(Currency.EUR, LocalDate.of(2026, 10, 15), "24.410")
                .publish(Currency.EUR, FRIDAY, "24.325");
        return new BudgetService(new InMemorySettingsRepository(), categories, transactions,
                new InMemoryBudgetLimitRepository(), rateCache,
                onlineCnb, Clock.fixed(TUESDAY));
    }

    private BudgetService serviceOn(LocalDate today) {
        return new BudgetService(new InMemorySettingsRepository(), categories, transactions,
                new InMemoryBudgetLimitRepository(), rateCache,
                cnb, Clock.fixed(today));
    }

    private Conversion convert(OriginalAmount amount, LocalDate date, Optional<Money> chargedAmount) {
        return service.convert(amount, date, chargedAmount).orElseThrow();
    }

    private Transaction record(LocalDate date, Conversion conversion) {
        return service.recordTransaction(
                TransactionDetails.converted(date, TransactionType.EXPENSE, games, conversion, "Steam"));
    }

    private static CnbRate fridayRate() {
        return new CnbRate(Currency.EUR, FRIDAY, new BigDecimal("24.325"));
    }

    private static OriginalAmount eur(long cents) {
        return new OriginalAmount(Currency.EUR, cents);
    }

    private static Money halere(long halere) {
        return Money.ofHalere(halere);
    }
}
