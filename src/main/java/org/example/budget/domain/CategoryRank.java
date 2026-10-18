package org.example.budget.domain;

import java.util.Objects;

/**
 * An Expense Category's net total over a period (Refunds subtracted, Savings-Funded Expenses included), of which
 * {@code fromSavings} was paid from Savings.
 */
public record CategoryRank(Category category, Money total, Money fromSavings) {

    public CategoryRank {
        Objects.requireNonNull(category);
        Objects.requireNonNull(total);
        Objects.requireNonNull(fromSavings);
    }

    public boolean hasFromSavings() {
        return fromSavings.halere() != 0;
    }
}
