package org.example.budget.domain;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

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
        String amount = formatAmount(locale);
        return isCzech(locale)
                ? amount + NBSP + "Kč"
                : "CZK" + NBSP + amount;
    }

    /** The number alone, without the currency, e.g. "1 234,50" for Czech: the form an amount is edited in. */
    public String formatAmount(Locale locale) {
        NumberFormat number = NumberFormat.getNumberInstance(locale);
        number.setMinimumFractionDigits(2);
        number.setMaximumFractionDigits(2);
        return number.format(BigDecimal.valueOf(halere, 2));
    }

    /**
     * Reads an amount typed by the user: an optional minus sign, digits with spaces as thousands separators
     * and up to two decimals. Czech accepts a decimal comma or point; English uses a decimal point and
     * allows commas as thousands separators. Empty when the text isn't such an amount.
     */
    public static Optional<Money> parse(String text, Locale locale) {
        if (text == null) {
            return Optional.empty();
        }
        String compact = text.replaceAll("[\\s\\u00A0\\u202F]", "");
        compact = isCzech(locale) ? compact.replace(',', '.') : compact.replace(",", "");
        if (!AMOUNT.matcher(compact).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Money(new BigDecimal(compact).movePointRight(2).longValueExact()));
        } catch (ArithmeticException tooLarge) {
            return Optional.empty();
        }
    }

    private static final Pattern AMOUNT = Pattern.compile("[-+]?\\d+(\\.\\d{1,2})?");

    private static boolean isCzech(Locale locale) {
        return "cs".equals(locale.getLanguage());
    }

    @Override
    public String toString() {
        return format(Locale.ENGLISH);
    }
}
