package org.example.budget.domain;

import java.util.List;
import java.util.Objects;

/**
 * What the Month dashboard shows for one Month: total Income, total Expenses (Refunds subtracted), the
 * Leftover, the Expenses per Category, largest first, and whether the Month is an Uncovered Deficit (a completed
 * Month whose Leftover is negative).
 */
public record MonthSummary(Month month, Money income, Money expenses, List<CategorySpending> expensesByCategory,
                           boolean deficit) {

    public MonthSummary {
        Objects.requireNonNull(month);
        Objects.requireNonNull(income);
        Objects.requireNonNull(expenses);
        expensesByCategory = List.copyOf(expensesByCategory);
    }

    /** Income minus Expenses. */
    public Money leftover() {
        return income.minus(expenses);
    }

    /** One Expense Category's net spending in the Month. */
    public record CategorySpending(Category category, Money total) {

        public CategorySpending {
            Objects.requireNonNull(category);
            Objects.requireNonNull(total);
        }
    }
}
