package org.example.budget.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A Month compared with the previous one. For the running Month, the Same-Point Comparison (up to today's day,
 * clamped to the previous Month's length) comes first; the comparison of the full Months is always there.
 */
public record MonthComparison(Month month, Optional<Comparison> samePoint, Comparison fullMonth) {

    public MonthComparison {
        Objects.requireNonNull(month);
        Objects.requireNonNull(samePoint);
        Objects.requireNonNull(fullMonth);
    }
}
