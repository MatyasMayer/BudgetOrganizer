package org.example.budget.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * How a foreign-currency Transaction got its CZK amount: the Original Amount, the ČNB Rate used and the
 * optional Charged Amount the bank actually took. A Charged Amount, when present, overrides the conversion.
 */
public record Conversion(OriginalAmount originalAmount, CnbRate cnbRate, Optional<Money> chargedAmount) {

    public Conversion {
        Objects.requireNonNull(originalAmount);
        Objects.requireNonNull(cnbRate);
        Objects.requireNonNull(chargedAmount);
        if (originalAmount.currency() != cnbRate.currency()) {
            throw new IllegalArgumentException("The ČNB Rate is for " + cnbRate.currency()
                    + ", not " + originalAmount.currency());
        }
    }

    /** The Original Amount at the ČNB Rate, rounded half-up to the haléř. */
    public Money convertedAmount() {
        return cnbRate.convert(originalAmount);
    }

    /** The CZK amount the Transaction counts with: the Charged Amount if present, else the converted amount. */
    public Money czkAmount() {
        return chargedAmount.orElseGet(this::convertedAmount);
    }
}
