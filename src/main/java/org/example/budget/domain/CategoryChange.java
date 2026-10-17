package org.example.budget.domain;

import java.util.Objects;

/** One Expense Category's spending in the compared period next to the previous period. */
public record CategoryChange(Category category, Change change) {

    public CategoryChange {
        Objects.requireNonNull(category);
        Objects.requireNonNull(change);
    }
}
