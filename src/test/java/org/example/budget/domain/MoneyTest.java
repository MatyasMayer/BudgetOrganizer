package org.example.budget.domain;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    private static final String NBSP = " ";

    @Test
    void holdsWholeHalere() {
        assertEquals(123_450, Money.ofHalere(123_450).halere());
    }

    @Test
    void addsSubtractsAndNegates() {
        Money salary = Money.ofHalere(3_000_000);
        Money rent = Money.ofHalere(1_200_050);

        assertEquals(Money.ofHalere(4_200_050), salary.plus(rent));
        assertEquals(Money.ofHalere(1_799_950), salary.minus(rent));
        assertEquals(Money.ofHalere(-1_200_050), rent.negate());
        assertEquals(Money.ZERO, rent.plus(rent.negate()));
    }

    @Test
    void refusesToOverflowSilently() {
        assertThrows(ArithmeticException.class, () -> Money.ofHalere(Long.MAX_VALUE).plus(Money.ofHalere(1)));
    }

    @Test
    void formatsInCzech() {
        Locale cs = Locale.forLanguageTag("cs-CZ");
        assertEquals("1" + NBSP + "234,50" + NBSP + "Kč", Money.ofHalere(123_450).format(cs));
        assertEquals("-1" + NBSP + "234,50" + NBSP + "Kč", Money.ofHalere(-123_450).format(cs));
        assertEquals("0,05" + NBSP + "Kč", Money.ofHalere(5).format(cs));
    }

    @Test
    void formatsInEnglish() {
        assertEquals("CZK" + NBSP + "1,234.50", Money.ofHalere(123_450).format(Locale.ENGLISH));
        assertEquals("CZK" + NBSP + "-1,234.50", Money.ofHalere(-123_450).format(Locale.ENGLISH));
        assertEquals("CZK" + NBSP + "1,000,000.00", Money.ofHalere(100_000_000).format(Locale.US));
    }
}
