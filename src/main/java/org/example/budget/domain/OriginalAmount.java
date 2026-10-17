package org.example.budget.domain;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The amount as paid in a currency other than CZK (e.g. €19.99), held in hundredths (cents).
 * Negative for a Refund.
 */
public record OriginalAmount(Currency currency, long cents) {

    private static final String NBSP = " ";

    public OriginalAmount {
        Objects.requireNonNull(currency);
        if (currency.isBaseCurrency()) {
            throw new IllegalArgumentException("An Original Amount is never in CZK");
        }
    }

    /** Reads an amount typed by the user, with the same rules as {@link Money#parse}. */
    public static Optional<OriginalAmount> parse(Currency currency, String text, Locale locale) {
        return Money.parse(text, locale).map(money -> new OriginalAmount(currency, money.halere()));
    }

    /** The number alone, e.g. "19,99" for Czech: the form an amount is edited in. */
    public String formatAmount(Locale locale) {
        NumberFormat number = NumberFormat.getNumberInstance(locale);
        number.setMinimumFractionDigits(2);
        number.setMaximumFractionDigits(2);
        return number.format(BigDecimal.valueOf(cents, 2));
    }

    /** E.g. "19,99 EUR" for Czech, "EUR 19.99" for any other language. */
    public String format(Locale locale) {
        return "cs".equals(locale.getLanguage())
                ? formatAmount(locale) + NBSP + currency.code()
                : currency.code() + NBSP + formatAmount(locale);
    }

    @Override
    public String toString() {
        return format(Locale.ENGLISH);
    }
}
