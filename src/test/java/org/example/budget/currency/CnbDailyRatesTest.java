package org.example.budget.currency;

import org.example.budget.domain.CnbRate;
import org.example.budget.domain.Currency;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Parses the real ČNB daily rates response saved on 16.10.2026 (Czech TXT variant). */
class CnbDailyRatesTest {

    static final String SAMPLE = "cnb-sample-2026-10-16.txt";

    private final CnbDailyRates rates = CnbDailyRates.parse(fixture(SAMPLE));

    @Test
    void theFixingDateComesFromTheFirstLine() {
        assertEquals(LocalDate.of(2026, 10, 16), rates.validFor());
    }

    @Test
    void theEuroRateIsReadWithTheCzechDecimalComma() {
        assertEquals(Optional.of(new CnbRate(Currency.EUR, LocalDate.of(2026, 10, 16), new BigDecimal("24.325"))),
                rates.rate(Currency.EUR));
    }

    @Test
    void aCurrencyQuotedPer100UnitsIsDividedByItsAmount() {
        assertEquals(Optional.of(new BigDecimal("0.13686")), rates.czkPerUnit("JPY"));
    }

    @Test
    void aCurrencyQuotedPer1000UnitsIsDividedByItsAmount() {
        assertEquals(Optional.of(new BigDecimal("0.001201")), rates.czkPerUnit("IDR"));
    }

    @Test
    void everyCurrencyLineIsRead() {
        assertEquals(30, rates.currencyCount());
        assertEquals(Optional.of(new BigDecimal("28.839")), rates.czkPerUnit("GBP"));
    }

    @Test
    void anUnlistedCurrencyIsAbsent() {
        assertEquals(Optional.empty(), rates.czkPerUnit("XYZ"));
    }

    @Test
    void theHeaderMayUseTwoSpacesBeforeTheSequenceNumber() {
        CnbDailyRates parsed = CnbDailyRates.parse("""
                09.10.2026  #195
                země|měna|množství|kód|kurz
                EMU|euro|1|EUR|24,410
                """);

        assertEquals(LocalDate.of(2026, 10, 9), parsed.validFor());
        assertEquals(Optional.of(new BigDecimal("24.41")), parsed.czkPerUnit("EUR"));
    }

    @Test
    void textThatIsNotADailyRatesFileIsRejected() {
        assertThrows(CnbFormatException.class, () -> CnbDailyRates.parse("<html>Service unavailable</html>"));
        assertThrows(CnbFormatException.class, () -> CnbDailyRates.parse(""));
        assertThrows(CnbFormatException.class, () -> CnbDailyRates.parse("""
                16.10.2026 #200
                země|měna|množství|kód|kurz
                EMU|euro|1|EUR|abc
                """));
    }

    static String fixture(String name) {
        try (InputStream in = CnbDailyRatesTest.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Missing fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
