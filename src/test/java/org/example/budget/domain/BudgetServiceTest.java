package org.example.budget.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Facade-level tests: the service runs against in-memory fakes and a fixed Clock. */
class BudgetServiceTest {

    private final InMemorySettingsRepository settings = new InMemorySettingsRepository();
    private final BudgetService service = new BudgetService(
            settings, new InMemoryCategoryRepository(), new InMemoryTransactionRepository(),
            new InMemoryBudgetLimitRepository(), Clock.fixed(LocalDate.of(2026, 10, 17)));

    @Test
    void theCurrentMonthComesFromTheClock() {
        assertEquals(Month.of(2026, 10), service.currentMonth());
    }

    @Test
    void openingSavingsAreZeroUntilSet() {
        assertEquals(Money.ZERO, service.openingSavings());
    }

    @Test
    void openingSavingsAreRememberedAcrossRestarts() {
        service.setOpeningSavings(Money.ofHalere(5_000_000));

        BudgetService afterRestart = new BudgetService(
                settings, new InMemoryCategoryRepository(), new InMemoryTransactionRepository(),
                new InMemoryBudgetLimitRepository(), Clock.fixed(LocalDate.of(2026, 11, 2)));

        assertEquals(Money.ofHalere(5_000_000), afterRestart.openingSavings());
    }
}
