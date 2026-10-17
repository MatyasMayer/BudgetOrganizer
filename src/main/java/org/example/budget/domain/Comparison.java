package org.example.budget.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Expenses per Category from {@code from} through {@code through}, compared with the previous Month from
 * {@code previousFrom} through {@code previousThrough}. A Category with Expenses in only one of the two periods
 * is still listed, with 0 in the other. Categories are ordered by current spending, largest first.
 */
public record Comparison(LocalDate from, LocalDate through, LocalDate previousFrom, LocalDate previousThrough,
                         List<CategoryChange> categories) {

    public Comparison {
        Objects.requireNonNull(from);
        Objects.requireNonNull(through);
        Objects.requireNonNull(previousFrom);
        Objects.requireNonNull(previousThrough);
        categories = List.copyOf(categories);
    }

    /** Total Expenses of both periods. */
    public Change total() {
        Money current = Money.ZERO;
        Money previous = Money.ZERO;
        for (CategoryChange category : categories) {
            current = current.plus(category.change().current());
            previous = previous.plus(category.change().previous());
        }
        return new Change(current, previous);
    }
}
