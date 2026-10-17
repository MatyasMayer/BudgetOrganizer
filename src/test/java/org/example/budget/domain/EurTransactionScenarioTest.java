package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Facade-level scenarios for EUR Transactions converted with the ČNB Rate, using a fake Rate source. */
class EurTransactionScenarioTest {

    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 16);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 18);
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

    private final FakeRateSource cnb = new FakeRateSource()
            .publish(Currency.EUR, LocalDate.of(2026, 10, 15), "24.410")
            .publish(Currency.EUR, FRIDAY, "24.325");
    private final InMemoryCnbRateRepository rateCache = new InMemoryCnbRateRepository();
    private final InMemoryCategoryRepository categories = new InMemoryCategoryRepository();
    private final InMemoryTransactionRepository transactions = new InMemoryTransactionRepository();
    private final BudgetService service = serviceOn(MONDAY);

    private long games;

    @BeforeEach
    void seed() {
        service.seedPresetCategories(PresetCategory::name);
        games = service.categories(TransactionType.EXPENSE).stream()
                .filter(c -> c.name().equals("GAMES")).findFirst().orElseThrow().id();
    }

    @Test
    void theCnbRateForTheTransactionsDateIsFilledIn() {
        assertEquals(Optional.of(new CnbRate(Currency.EUR, FRIDAY, new BigDecimal("24.325"))),
                service.cnbRate(Currency.EUR, FRIDAY));
        assertEquals(Optional.of(new CnbRate(Currency.EUR, LocalDate.of(2026, 10, 15), new BigDecimal("24.41"))),
                service.cnbRate(Currency.EUR, LocalDate.of(2026, 10, 15)));
    }

    @Test
    void aEurExpenseCountsWithItsAmountAtTheCnbRate() {
        Transaction hades = record(FRIDAY, convert(eur(1_999), FRIDAY, Optional.empty()), "Steam – Hades II");

        // 19.99 EUR × 24.325 = 486.25675 CZK
        assertEquals(Money.ofHalere(48_626), hades.amount());
        assertEquals(Money.ofHalere(48_626), service.categoryTotal(games, Month.of(2026, 10)));
    }

    @Test
    void theConversionRoundsHalfUpToTheHaler() {
        cnb.publish(Currency.EUR, LocalDate.of(2026, 10, 1), "24.35");

        Conversion conversion = convert(eur(30), LocalDate.of(2026, 10, 1), Optional.empty());

        // 0.30 EUR × 24.35 = 7.305 CZK, half-up to 7.31 (half-even would give 7.30)
        assertEquals(Money.ofHalere(731), conversion.czkAmount());
    }

    @Test
    void aNegativeHalfIsRoundedAwayFromZeroForARefund() {
        cnb.publish(Currency.EUR, LocalDate.of(2026, 10, 1), "24.35");

        Conversion refund = convert(eur(-30), LocalDate.of(2026, 10, 1), Optional.empty());

        assertEquals(Money.ofHalere(-731), refund.czkAmount());
    }

    @Test
    void theChargedAmountOverridesTheConversion() {
        Transaction hades = record(FRIDAY, convert(eur(1_999), FRIDAY, Optional.of(halere(49_310))), "Steam");

        assertEquals(Money.ofHalere(49_310), hades.amount());
        assertEquals(Money.ofHalere(48_626), hades.details().conversion().orElseThrow().convertedAmount());
        assertEquals(Money.ofHalere(49_310), service.categoryTotal(games, Month.of(2026, 10)));
    }

    @Test
    void theOriginalAmountRateAndChargedAmountAreKeptWhenOpenedAgain() {
        Conversion conversion = convert(eur(1_999), FRIDAY, Optional.of(halere(49_310)));
        Transaction hades = record(FRIDAY, conversion, "Steam – Hades II");

        Transaction reopened = service.transactions(TransactionFilter.ALL).getFirst();

        assertEquals(hades, reopened);
        assertEquals(Currency.EUR, reopened.details().currency());
        assertEquals(eur(1_999), reopened.details().conversion().orElseThrow().originalAmount());
        assertEquals(new BigDecimal("24.325"), reopened.details().conversion().orElseThrow().cnbRate().czkPerUnit());
        assertEquals(Optional.of(halere(49_310)), reopened.details().conversion().orElseThrow().chargedAmount());
    }

    @Test
    void aWeekendTransactionUsesTheFixingCnbGivesForThatDay() {
        Conversion conversion = convert(eur(1_000), SUNDAY, Optional.empty());

        assertEquals(FRIDAY, conversion.cnbRate().validFor());
        assertEquals(Money.ofHalere(24_325), conversion.czkAmount());
    }

    @Test
    void aCachedRateIsUsedWithoutAskingCnbAgain() {
        service.cnbRate(Currency.EUR, FRIDAY);
        service.cnbRate(Currency.EUR, FRIDAY);
        record(FRIDAY, convert(eur(500), FRIDAY, Optional.empty()), "");

        assertEquals(1, cnb.requests());
    }

    @Test
    void theCacheOutlivesARestart() {
        service.cnbRate(Currency.EUR, SUNDAY);

        BudgetService afterRestart = serviceOn(MONDAY.plusDays(1));
        Optional<CnbRate> rate = afterRestart.cnbRate(Currency.EUR, SUNDAY);

        assertEquals(1, cnb.requests());
        assertEquals(FRIDAY, rate.orElseThrow().validFor());
    }

    @Test
    void eachDateIsCachedSeparately() {
        service.cnbRate(Currency.EUR, FRIDAY);
        service.cnbRate(Currency.EUR, SUNDAY);

        assertEquals(2, cnb.requests());
    }

    @Test
    void todaysRateIsNotCachedBeforeCnbPublishesIt() {
        // Monday morning: ČNB still answers with Friday's fixing, Monday's comes at 14:30.
        assertEquals(FRIDAY, service.cnbRate(Currency.EUR, MONDAY).orElseThrow().validFor());

        cnb.publish(Currency.EUR, MONDAY, "24.300");

        assertEquals(MONDAY, service.cnbRate(Currency.EUR, MONDAY).orElseThrow().validFor());
        assertEquals(2, cnb.requests());
    }

    @Test
    void aRateFetchedForTodayStillServesItsFixingDateFromTheCache() {
        service.cnbRate(Currency.EUR, MONDAY);

        service.cnbRate(Currency.EUR, FRIDAY);

        assertEquals(1, cnb.requests());
    }

    @Test
    void anUnavailableRateLeavesNothingToConvertWith() {
        cnb.goOffline();

        assertEquals(Optional.empty(), service.cnbRate(Currency.EUR, FRIDAY));
        assertEquals(Optional.empty(), service.convert(eur(1_999), FRIDAY, Optional.empty()));
    }

    @Test
    void aEurIncomeCannotBeNegative() {
        long gift = service.categories(TransactionType.INCOME).getFirst().id();
        Conversion negative = convert(eur(-1_000), FRIDAY, Optional.empty());

        InvalidTransactionException error = assertThrows(InvalidTransactionException.class, () ->
                service.recordTransaction(TransactionDetails.converted(
                        FRIDAY, TransactionType.INCOME, gift, negative, "")));

        assertEquals(InvalidTransactionException.Problem.NEGATIVE_INCOME, error.problem());
    }

    @Test
    void anOriginalAmountOfZeroIsRejectedEvenWithAChargedAmount() {
        Conversion zero = convert(eur(0), FRIDAY, Optional.of(halere(100)));

        InvalidTransactionException error = assertThrows(InvalidTransactionException.class, () ->
                record(FRIDAY, zero, ""));

        assertEquals(InvalidTransactionException.Problem.ZERO_AMOUNT, error.problem());
        assertEquals(List.of(), service.transactions(TransactionFilter.ALL));
    }

    @Test
    void aEurTransactionCanBeEditedIntoCzk() {
        Transaction hades = record(FRIDAY, convert(eur(1_999), FRIDAY, Optional.empty()), "Steam");

        TransactionDetails inCzk = new TransactionDetails(FRIDAY, TransactionType.EXPENSE, games, halere(50_000), "Steam");
        service.editTransaction(hades.id(), inCzk);

        assertEquals(Currency.CZK, service.transactions(TransactionFilter.ALL).getFirst().details().currency());
        assertEquals(halere(50_000), service.categoryTotal(games, Month.of(2026, 10)));
    }

    private BudgetService serviceOn(LocalDate today) {
        return new BudgetService(new InMemorySettingsRepository(), categories, transactions,
                rateCache, cnb, Clock.fixed(today));
    }

    private Conversion convert(OriginalAmount amount, LocalDate date, Optional<Money> chargedAmount) {
        return service.convert(amount, date, chargedAmount).orElseThrow();
    }

    private Transaction record(LocalDate date, Conversion conversion, String note) {
        return service.recordTransaction(
                TransactionDetails.converted(date, TransactionType.EXPENSE, games, conversion, note));
    }

    private static OriginalAmount eur(long cents) {
        return new OriginalAmount(Currency.EUR, cents);
    }

    private static Money halere(long halere) {
        return Money.ofHalere(halere);
    }
}
