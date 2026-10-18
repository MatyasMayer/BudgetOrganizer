package org.example.budget.domain;

import java.util.Objects;

/**
 * An Expense Category's net total over a period (Refunds subtracted, Savings-Funded Expenses included), of which
 * {@code savingsFunded} was paid from Savings.
 */
public record CategoryRank(Category category, Money total, Money savingsFunded) {

    public CategoryRank {
        Objects.requireNonNull(category);
        Objects.requireNonNull(total);
        Objects.requireNonNull(savingsFunded);
    }

    public boolean hasSavingsFunded() {
        return savingsFunded.halere() != 0;
    }
}
