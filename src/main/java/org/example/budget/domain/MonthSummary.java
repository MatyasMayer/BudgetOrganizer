package org.example.budget.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    /**
     * One Expense Category's net spending in the Month, and its effective Budget Limit if it has one.
     * {@code total} counts toward the Leftover and the Budget Limit; {@code savingsFunded} is what the Category's
     * Savings-Funded Expenses took from Savings, shown in its slice with a "from Savings" marker.
     */
    public record CategorySpending(Category category, Money total, Optional<Money> limit, Money savingsFunded) {

        public CategorySpending {
            Objects.requireNonNull(category);
            Objects.requireNonNull(total);
            Objects.requireNonNull(limit);
            Objects.requireNonNull(savingsFunded);
        }

        /** Spending without Savings-Funded Expenses. */
        public CategorySpending(Category category, Money total, Optional<Money> limit) {
            this(category, total, limit, Money.ZERO);
        }

        /** Everything spent in the Category, Savings-Funded Expenses included: the size of its pie slice. */
        public Money spent() {
            return total.plus(savingsFunded);
        }

        public boolean hasSavingsFunded() {
            return savingsFunded.halere() != 0;
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
