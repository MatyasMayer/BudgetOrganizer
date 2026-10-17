package org.example.budget.domain;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Optional;

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
    void parsesCzechInput() {
        Locale cs = Locale.forLanguageTag("cs-CZ");
        assertEquals(Optional.of(Money.ofHalere(123_450)), Money.parse("1 234,50", cs));
        assertEquals(Optional.of(Money.ofHalere(123_450)), Money.parse("1" + NBSP + "234,5", cs));
        assertEquals(Optional.of(Money.ofHalere(1_250)), Money.parse("12.50", cs));
        assertEquals(Optional.of(Money.ofHalere(-60_000)), Money.parse(" -600 ", cs));
    }

    @Test
    void parsesEnglishInput() {
        assertEquals(Optional.of(Money.ofHalere(123_450)), Money.parse("1,234.50", Locale.ENGLISH));
        assertEquals(Optional.of(Money.ofHalere(18_900)), Money.parse("189", Locale.ENGLISH));
        assertEquals(Optional.of(Money.ofHalere(-5)), Money.parse("-0.05", Locale.ENGLISH));
    }

    @Test
    void rejectsInputThatIsNotAnAmount() {
        Locale cs = Locale.forLanguageTag("cs");
        assertEquals(Optional.empty(), Money.parse("", cs));
        assertEquals(Optional.empty(), Money.parse("abc", cs));
        assertEquals(Optional.empty(), Money.parse("12,345", cs));
        assertEquals(Optional.empty(), Money.parse("1,2,3", cs));
        assertEquals(Optional.empty(), Money.parse(null, cs));
        assertEquals(Optional.empty(), Money.parse("99999999999999999999999", cs));
    }

    @Test
    void formatsTheBareAmountForEditing() {
        assertEquals("-1" + NBSP + "234,50", Money.ofHalere(-123_450).formatAmount(Locale.forLanguageTag("cs")));
        assertEquals("1,234.50", Money.ofHalere(123_450).formatAmount(Locale.ENGLISH));
    }

    @Test
    void parsingReadsBackWhatFormattingWrote() {
        for (Locale locale : new Locale[] {Locale.ENGLISH, Locale.forLanguageTag("cs-CZ")}) {
            Money amount = Money.ofHalere(-98_765_432);
            assertEquals(Optional.of(amount), Money.parse(amount.formatAmount(locale), locale));
        }
    }

    @Test
    void formatsInEnglish() {
        assertEquals("CZK" + NBSP + "1,234.50", Money.ofHalere(123_450).format(Locale.ENGLISH));
        assertEquals("CZK" + NBSP + "-1,234.50", Money.ofHalere(-123_450).format(Locale.ENGLISH));
        assertEquals("CZK" + NBSP + "1,000,000.00", Money.ofHalere(100_000_000).format(Locale.US));
    }
}
