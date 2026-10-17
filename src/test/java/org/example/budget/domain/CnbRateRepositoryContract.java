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
}
