package org.example.budget.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * How a foreign-currency Transaction got its CZK amount: the Original Amount, the ČNB Rate used and the
 * optional Charged Amount the bank actually took. A Charged Amount, when present, overrides the conversion.
 * <p>
 * {@code provisionalRate} marks a Provisional Rate: not the ČNB Rate for the Transaction's date (which couldn't
 * be fetched) but a stand-in, replaced by "refresh Provisional Rates" once ČNB can be reached.
 */
public record Conversion(OriginalAmount originalAmount, CnbRate cnbRate, Optional<Money> chargedAmount,
                         boolean provisionalRate) {

    public Conversion {
        Objects.requireNonNull(originalAmount);
        Objects.requireNonNull(cnbRate);
        Objects.requireNonNull(chargedAmount);
        if (originalAmount.currency() != cnbRate.currency()) {
            throw new IllegalArgumentException("The ČNB Rate is for " + cnbRate.currency()
                    + ", not " + originalAmount.currency());
        }
    }

    /** A conversion with the ČNB Rate for the Transaction's date. */
    public Conversion(OriginalAmount originalAmount, CnbRate cnbRate, Optional<Money> chargedAmount) {
        this(originalAmount, cnbRate, chargedAmount, false);
    }

    /** A conversion with a Provisional Rate, to be replaced once the ČNB Rate for the date is available. */
    public static Conversion provisional(OriginalAmount originalAmount, CnbRate rate, Optional<Money> chargedAmount) {
        return new Conversion(originalAmount, rate, chargedAmount, true);
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
