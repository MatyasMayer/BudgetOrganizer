package org.example.budget.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * The rate a foreign-currency Transaction can use right now: the ČNB Rate for its date, or, when that can't be
 * fetched, the most recent cached rate as a Provisional Rate.
 */
public record AvailableRate(CnbRate rate, boolean provisional) {

    public AvailableRate {
        Objects.requireNonNull(rate);
    }

    public Conversion convert(OriginalAmount amount, Optional<Money> chargedAmount) {
        return new Conversion(amount, rate, chargedAmount, provisional);
    }
}
