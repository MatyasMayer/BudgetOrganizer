package org.example.budget.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * The most an Expense Category is planned to spend in a Month: either its Default Limit (no Month) or a
 * Limit Override for one specific Month.
 */
public record BudgetLimit(long categoryId, Optional<Month> month, Money amount) {

    public BudgetLimit {
        Objects.requireNonNull(month);
        Objects.requireNonNull(amount);
    }

    public static BudgetLimit defaultLimit(long categoryId, Money amount) {
        return new BudgetLimit(categoryId, Optional.empty(), amount);
    }

    public static BudgetLimit override(long categoryId, Month month, Money amount) {
        return new BudgetLimit(categoryId, Optional.of(month), amount);
    }

    public boolean isDefault() {
        return month.isEmpty();
    }
}
