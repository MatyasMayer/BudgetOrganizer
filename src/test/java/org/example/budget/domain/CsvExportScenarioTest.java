package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Facade-level scenarios for the CSV export: all Transactions or the current filter. */
class CsvExportScenarioTest {

    private static final java.util.List<String> HEADER = TransactionCsv.COLUMN_KEYS;
    private static final String HEADER_LINE = String.join(";", HEADER) + "\r\n";

    private final BudgetService service = new BudgetService(
            new InMemorySettingsRepository(), new InMemoryCategoryRepository(), new InMemoryTransactionRepository(),
            new InMemoryRecurringTemplateRepository(), new InMemoryBudgetLimitRepository(),
            Clock.fixed(LocalDate.of(2026, 10, 17)));

    @BeforeEach
    void seed() {
        service.seedPresetCategories(PresetCategory::name);
    }

    @Test
    void everyTransactionIsExportedWithItsCategoryNameNewestFirst() {
        expense(LocalDate.of(2026, 9, 1), "RENT", 12_000, "Rent; September");
        expense(LocalDate.of(2026, 10, 2), "FOOD", 250, "Lunch");

        assertEquals("﻿" + HEADER_LINE
                + "2026-10-02;EXPENSE;FOOD;250,00;;;;;0;Lunch\r\n"
                + "2026-09-01;EXPENSE;RENT;12000,00;;;;;0;\"Rent; September\"\r\n",
                service.transactionsCsv(TransactionFilter.ALL, HEADER));
    }

    @Test
    void theCurrentFilterLimitsWhatIsExported() {
        expense(LocalDate.of(2026, 9, 1), "RENT", 12_000, "");
        expense(LocalDate.of(2026, 10, 2), "FOOD", 250, "Lunch");

        String csv = service.transactionsCsv(TransactionFilter.ALL.inMonth(Month.of(2026, 10)), HEADER);

        assertTrue(csv.contains("FOOD"));
        assertEquals(2, csv.lines().count());
    }

    @Test
    void eurAndSavingsFundedTransactionsKeepTheirDetails() {
        Conversion conversion = new Conversion(new OriginalAmount(Currency.EUR, 1_099),
                new CnbRate(Currency.EUR, LocalDate.of(2026, 10, 5), new BigDecimal("24.325")),
                Optional.of(Money.ofHalere(27_000)));
        service.recordTransaction(TransactionDetails.converted(
                LocalDate.of(2026, 10, 5), TransactionType.EXPENSE, id("SUBSCRIPTIONS"), conversion, "Spotify"));
        service.recordTransaction(new TransactionDetails(LocalDate.of(2026, 10, 6), TransactionType.EXPENSE,
                id("ELECTRONICS"), Money.ofHalere(2_500_000), "PC").withSavingsFunded(true));

        String csv = service.transactionsCsv(TransactionFilter.ALL, HEADER);

        assertTrue(csv.contains("2026-10-05;EXPENSE;SUBSCRIPTIONS;270,00;10,99;EUR;24,325;270,00;0;Spotify"));
        assertTrue(csv.contains("2026-10-06;EXPENSE;ELECTRONICS;25000,00;;;;;1;PC"));
    }

    private void expense(LocalDate date, String category, long czk, String note) {
        service.recordTransaction(new TransactionDetails(date, TransactionType.EXPENSE, id(category),
                Money.ofHalere(czk * 100), note));
    }

    private long id(String name) {
        return Stream.of(TransactionType.values())
                .flatMap(type -> service.categories(type).stream())
                .filter(category -> category.name().equals(name))
                .findFirst().orElseThrow().id();
    }
}
