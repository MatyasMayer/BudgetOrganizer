package org.example.budget.domain;

import java.util.Objects;

/** The Pace and safe-to-spend per day of a Category with an effective Budget Limit. */
public record CategoryPace(Category category, Money pace, Money limit, Money safeToSpendPerDay) {

    public CategoryPace {
        Objects.requireNonNull(category);
        Objects.requireNonNull(pace);
        Objects.requireNonNull(limit);
        Objects.requireNonNull(safeToSpendPerDay);
    }

    /** Whether the Month is heading above the limit. */
    public boolean exceedsLimit() {
        return pace.halere() > limit.halere();
    }
}
