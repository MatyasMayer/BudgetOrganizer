package org.example.budget.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the Month dashboard shows for one Month: total Income, total Expenses (Refunds subtracted), the
 * Leftover, and the Expenses per Category, largest first.
 */
public record MonthSummary(Month month, Money income, Money expenses, List<CategorySpending> expensesByCategory) {

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

    /** One Expense Category's net spending in the Month, and its effective Budget Limit if it has one. */
    public record CategorySpending(Category category, Money total, Optional<Money> limit) {

        public CategorySpending {
            Objects.requireNonNull(category);
            Objects.requireNonNull(total);
            Objects.requireNonNull(limit);
        }

        /** Spending of a Category without a Budget Limit. */
        public CategorySpending(Category category, Money total) {
            this(category, total, Optional.empty());
        }

        /** The progress bar's colour; none without a limit, since then only the spending is shown. */
        public Optional<BudgetBarStatus> barStatus() {
            return limit.map(max -> {
                // Whole haléře compared as spent * 100 against limit * 80, so no rounding is involved.
                if (Math.multiplyExact(total.halere(), 100) < Math.multiplyExact(max.halere(), 80)) {
                    return BudgetBarStatus.GREEN;
                }
                return total.halere() <= max.halere() ? BudgetBarStatus.ORANGE : BudgetBarStatus.RED;
            });
        }
    }
}
