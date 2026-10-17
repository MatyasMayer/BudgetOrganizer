package org.example.budget.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A ČNB Rate: how many CZK one unit of a currency cost in the ČNB fixing published for {@code validFor}.
 * For weekends and holidays that is the last working day's fixing, so {@code validFor} can be earlier
 * than the date it was asked for.
 */
public record CnbRate(Currency currency, LocalDate validFor, BigDecimal czkPerUnit) {

    public CnbRate {
        Objects.requireNonNull(currency);
        Objects.requireNonNull(validFor);
        if (currency.isBaseCurrency()) {
            throw new IllegalArgumentException("CZK has no ČNB Rate");
        }
        if (czkPerUnit.signum() <= 0) {
            throw new IllegalArgumentException("A ČNB Rate must be positive: " + czkPerUnit);
        }
        // 24.325 and 24.3250 are the same rate; keep one form so equal rates are equal records.
        czkPerUnit = czkPerUnit.stripTrailingZeros();
    }

    /** The Original Amount in CZK, rounded half-up to the haléř. */
    public Money convert(OriginalAmount amount) {
        if (amount.currency() != currency) {
            throw new IllegalArgumentException("A " + currency + " rate can't convert " + amount.currency());
        }
        BigDecimal halere = BigDecimal.valueOf(amount.cents()).multiply(czkPerUnit)
                .setScale(0, RoundingMode.HALF_UP);
        return Money.ofHalere(halere.longValueExact());
    }
}
