package org.example.budget.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/** One amount in the compared period next to the same amount in the previous period. */
public record Change(Money current, Money previous) {

    public Change {
        Objects.requireNonNull(current);
        Objects.requireNonNull(previous);
    }

    /** Current minus previous, in CZK. */
    public Money difference() {
        return current.minus(previous);
    }

    /** The difference as a fraction of the previous value (0.2000 = +20 %); empty when the previous value is 0. */
    public Optional<BigDecimal> percent() {
        if (previous.halere() == 0) {
            return Optional.empty();
        }
        return Optional.of(BigDecimal.valueOf(difference().halere())
                .divide(BigDecimal.valueOf(previous.halere()), 4, RoundingMode.HALF_UP));
    }
}
