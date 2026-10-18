package org.example.budget.domain;

import java.util.List;

/**
 * What the Top spending screen shows for a Month or a year: the Expense Categories ranked by total, largest
 * first, and the five biggest single Expenses. Savings-Funded Expenses are included and marked.
 */
public record TopSpending(List<CategoryRank> categories, List<Transaction> biggestExpenses) {

    public TopSpending {
        categories = List.copyOf(categories);
        biggestExpenses = List.copyOf(biggestExpenses);
    }
}
