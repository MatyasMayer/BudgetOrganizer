package org.example.budget.domain;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * An amount in the Base Currency (CZK), held as a whole number of haléře. Never floating point.
 */
public record Money(long halere) {

    public static final Money ZERO = new Money(0);

    private static final String NBSP = " ";

    public static Money ofHalere(long halere) {
        return new Money(halere);
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(halere, other.halere));
    }

    public Money minus(Money other) {
        return new Money(Math.subtractExact(halere, other.halere));
    }

    public Money negate() {
        return new Money(Math.negateExact(halere));
    }

    /** E.g. "1 234,50 Kč" for Czech, "CZK 1,234.50" for any other language. */
    public String format(Locale locale) {
        NumberFormat number = NumberFormat.getNumberInstance(locale);
        number.setMinimumFractionDigits(2);
        number.setMaximumFractionDigits(2);
        String amount = number.format(BigDecimal.valueOf(halere, 2));
        return "cs".equals(locale.getLanguage())
                ? amount + NBSP + "Kč"
                : "CZK" + NBSP + amount;
    }

    @Override
    public String toString() {
        return format(Locale.ENGLISH);
    }
}
