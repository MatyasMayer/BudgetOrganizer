package org.example.budget.domain;

import java.util.Objects;

/** One Month's total Income and total Expenses (Refunds subtracted). */
public record MonthTotals(Month month, Money income, Money expenses) {

    public MonthTotals {
        Objects.requireNonNull(month);
        Objects.requireNonNull(income);
        Objects.requireNonNull(expenses);
    }

    /** Income minus Expenses. */
    public Money leftover() {
        return income.minus(expenses);
    }
}
