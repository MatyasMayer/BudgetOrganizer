package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The shared suite every {@link CnbRateRepository} must pass (in-memory fake and SQLite). */
public abstract class CnbRateRepositoryContract {

    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 16);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 17);
    private static final CnbRate FRIDAY_EUR = new CnbRate(Currency.EUR, FRIDAY, new BigDecimal("24.325"));

    private CnbRateRepository repository;

    protected abstract CnbRateRepository createRepository() throws Exception;

    @BeforeEach
    void setUp() throws Exception {
        repository = createRepository();
    }

    @Test
    void anUncachedRateIsAbsent() {
        assertEquals(Optional.empty(), repository.find(Currency.EUR, FRIDAY));
    }

    @Test
    void aSavedRateIsFoundByItsDateAndCurrency() {
        repository.save(FRIDAY, FRIDAY_EUR);

        assertEquals(Optional.of(FRIDAY_EUR), repository.find(Currency.EUR, FRIDAY));
        assertEquals(Optional.empty(), repository.find(Currency.EUR, SATURDAY));
    }

    @Test
    void aWeekendDateKeepsTheFixingDateItsRateBelongsTo() {
        repository.save(SATURDAY, FRIDAY_EUR);

        assertEquals(Optional.of(FRIDAY_EUR), repository.find(Currency.EUR, SATURDAY));
        assertEquals(Optional.empty(), repository.find(Currency.EUR, FRIDAY));
    }

    @Test
    void theRateKeepsAllItsDecimals() {
        CnbRate precise = new CnbRate(Currency.EUR, FRIDAY, new BigDecimal("0.0012013579"));

        repository.save(FRIDAY, precise);

        assertEquals(Optional.of(precise), repository.find(Currency.EUR, FRIDAY));
    }

    @Test
    void savingTheSameDateAgainReplacesTheRate() {
        CnbRate corrected = new CnbRate(Currency.EUR, FRIDAY, new BigDecimal("24.33"));

        repository.save(FRIDAY, FRIDAY_EUR);
        repository.save(FRIDAY, corrected);

        assertEquals(Optional.of(corrected), repository.find(Currency.EUR, FRIDAY));
    }

    @Test
    void withNothingCachedThereIsNoMostRecentRate() {
        assertEquals(Optional.empty(), repository.mostRecent(Currency.EUR));
    }

    @Test
    void theMostRecentRateIsTheOneWithTheLatestFixing() {
        CnbRate thursday = new CnbRate(Currency.EUR, FRIDAY.minusDays(1), new BigDecimal("24.41"));
        repository.save(SATURDAY, FRIDAY_EUR);
        repository.save(FRIDAY.minusDays(1), thursday);

        assertEquals(Optional.of(FRIDAY_EUR), repository.mostRecent(Currency.EUR));
    }

    @Test
    void theMostRecentRateComparesFixingDatesAcrossYearsAndMonths() {
        CnbRate december = new CnbRate(Currency.EUR, LocalDate.of(2025, 12, 31), new BigDecimal("25.1"));
        CnbRate january = new CnbRate(Currency.EUR, LocalDate.of(2026, 1, 2), new BigDecimal("24.9"));
        repository.save(january.validFor(), january);
        repository.save(december.validFor(), december);

        assertEquals(Optional.of(january), repository.mostRecent(Currency.EUR));
    }
}
