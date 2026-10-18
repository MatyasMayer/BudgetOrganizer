package org.example.budget.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The CSV export: semicolon-separated for Czech-locale Excel, with a UTF-8 byte order mark added when written. */
class TransactionCsvTest {

    private static final List<String> HEADER = List.of("Date", "Type", "Category", "CZK amount", "Original Amount",
            "Currency", "CNB Rate", "Charged Amount", "Savings-Funded", "Note");
    private static final String HEADER_LINE =
            "Date;Type;Category;CZK amount;Original Amount;Currency;CNB Rate;Charged Amount;Savings-Funded;Note";

    private static String csv(Transaction... transactions) {
        return TransactionCsv.render(HEADER, List.of(transactions), id -> id == 1 ? "Food" : "Salary");
    }

    private static Transaction czk(TransactionType type, long categoryId, long halere, String note) {
        return new Transaction(1, new TransactionDetails(LocalDate.of(2026, 10, 5), type, categoryId,
                Money.ofHalere(halere), note));
    }

    @Test
    void theFileStartsWithTheUtf8ByteOrderMarkAndTheHeaderRow() {
        String content = csv();

        assertTrue(content.startsWith("﻿" + HEADER_LINE));
        assertEquals("﻿" + HEADER_LINE + "\r\n", content);
    }

    @Test
    void aCzkTransactionHasNoForeignCurrencyColumns() {
        assertEquals("﻿" + HEADER_LINE + "\r\n2026-10-05;EXPENSE;Food;1234,50;;;;;0;Lunch\r\n",
                csv(czk(TransactionType.EXPENSE, 1, 123_450, "Lunch")));
    }

    @Test
    void anIncomeAndARefundAreExportedWithTheirSigns() {
        String content = csv(czk(TransactionType.INCOME, 2, 3_000_000, ""),
                czk(TransactionType.EXPENSE, 1, -50_000, "Returned"));

        assertTrue(content.contains("\r\n2026-10-05;INCOME;Salary;30000,00;;;;;0;\r\n"));
        assertTrue(content.endsWith("\r\n2026-10-05;EXPENSE;Food;-500,00;;;;;0;Returned\r\n"));
    }

    @Test
    void aEurTransactionShowsItsOriginalAmountCurrencyAndCnbRate() {
        Conversion conversion = new Conversion(new OriginalAmount(Currency.EUR, 1_099),
                new CnbRate(Currency.EUR, LocalDate.of(2026, 10, 5), new BigDecimal("24.325")), Optional.empty());
        Transaction spotify = new Transaction(1, TransactionDetails.converted(
                LocalDate.of(2026, 10, 5), TransactionType.EXPENSE, 1, conversion, "Spotify"));

        assertTrue(csv(spotify).endsWith("2026-10-05;EXPENSE;Food;267,33;10,99;EUR;24,325;;0;Spotify\r\n"));
    }

    @Test
    void aChargedAmountIsExportedBesideTheConvertedOne() {
        Conversion conversion = new Conversion(new OriginalAmount(Currency.EUR, 1_099),
                new CnbRate(Currency.EUR, LocalDate.of(2026, 10, 5), new BigDecimal("24.325")),
                Optional.of(Money.ofHalere(27_000)));
        Transaction spotify = new Transaction(1, TransactionDetails.converted(
                LocalDate.of(2026, 10, 5), TransactionType.EXPENSE, 1, conversion, "Spotify"));

        assertTrue(csv(spotify).endsWith("2026-10-05;EXPENSE;Food;270,00;10,99;EUR;24,325;270,00;0;Spotify\r\n"));
    }

    @Test
    void aSavingsFundedExpenseIsMarked() {
        Transaction pc = new Transaction(1, new TransactionDetails(LocalDate.of(2026, 10, 5), TransactionType.EXPENSE,
                1, Money.ofHalere(2_500_000), "PC").withSavingsFunded(true));

        assertTrue(csv(pc).endsWith("2026-10-05;EXPENSE;Food;25000,00;;;;;1;PC\r\n"));
    }

    @Test
    void semicolonsQuotesAndLineBreaksInANoteAreQuoted() {
        assertTrue(csv(czk(TransactionType.EXPENSE, 1, 100, "a;b")).endsWith(";0;\"a;b\"\r\n"));
        assertTrue(csv(czk(TransactionType.EXPENSE, 1, 100, "say \"hi\"")).endsWith(";0;\"say \"\"hi\"\"\"\r\n"));
        assertTrue(csv(czk(TransactionType.EXPENSE, 1, 100, "one\ntwo")).endsWith(";0;\"one\ntwo\"\r\n"));
    }

    @Test
    void aCategoryNameWithASemicolonIsQuotedToo() {
        String content = TransactionCsv.render(HEADER, List.of(czk(TransactionType.EXPENSE, 1, 100, "")),
                id -> "Food; drink");

        assertTrue(content.endsWith(";EXPENSE;\"Food; drink\";1,00;;;;;0;\r\n"));
    }

    @Test
    void aNoteThatWouldRunAsAFormulaInExcelIsDefused() {
        assertTrue(csv(czk(TransactionType.EXPENSE, 1, 100, "=SUM(A1)")).endsWith(";0;'=SUM(A1)\r\n"));
        assertTrue(csv(czk(TransactionType.EXPENSE, 1, 100, "@cmd")).endsWith(";0;'@cmd\r\n"));
        assertTrue(csv(czk(TransactionType.EXPENSE, 1, 100, "-1 pcs")).endsWith(";0;'-1 pcs\r\n"));
    }

    @Test
    void transactionsKeepTheOrderTheyWereGivenIn() {
        Transaction first = new Transaction(1, new TransactionDetails(LocalDate.of(2026, 10, 7),
                TransactionType.EXPENSE, 1, Money.ofHalere(100), "newer"));
        Transaction second = new Transaction(2, new TransactionDetails(LocalDate.of(2026, 10, 1),
                TransactionType.EXPENSE, 1, Money.ofHalere(100), "older"));

        String content = csv(first, second);

        assertTrue(content.indexOf("newer") < content.indexOf("older"));
    }
}
