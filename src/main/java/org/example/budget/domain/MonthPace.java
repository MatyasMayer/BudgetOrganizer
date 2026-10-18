package org.example.budget.domain;

import java.util.List;
import java.util.Objects;

/**
 * Where the running Month is heading: the overall Pace (Variable Spending extrapolated, plus Fixed Spending),
 * the daily average of Variable Spending so far, the overall safe-to-spend per day for the rest of the Month,
 * and the same per limited Category.
 */
public record MonthPace(Month month, Money pace, Money dailyAverage, Money safeToSpendPerDay,
                        List<CategoryPace> categories) {

    public MonthPace {
        Objects.requireNonNull(month);
        Objects.requireNonNull(pace);
        Objects.requireNonNull(dailyAverage);
        Objects.requireNonNull(safeToSpendPerDay);
        categories = List.copyOf(categories);
    }
}
