package org.example.budget.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * One Month in the year summary. {@code savingsFunded} is what its Savings-Funded Expenses took from Savings:
 * shown beside the Month, but not part of {@code expenses} or the Leftover. {@code savingsRate} is empty when
 * the Month has no Income other than From Savings.
 */
public record MonthOfYear(Month month, Money income, Money expenses, Money savingsFunded, boolean deficit,
                          Optional<BigDecimal> savingsRate) {

    public MonthOfYear {
        Objects.requireNonNull(month);
        Objects.requireNonNull(income);
        Objects.requireNonNull(expenses);
        Objects.requireNonNull(savingsFunded);
        Objects.requireNonNull(savingsRate);
    }

    /** Income minus Expenses. */
    public Money leftover() {
        return income.minus(expenses);
    }
}
