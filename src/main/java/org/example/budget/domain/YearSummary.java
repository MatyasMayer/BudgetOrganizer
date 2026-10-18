package org.example.budget.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A year's 12 Months with the year totals of Income, Expenses and Leftover, and the average Savings Rate: the
 * mean of the rates of the Months that have one.
 */
public record YearSummary(int year, List<MonthOfYear> months) {

    public YearSummary {
        Objects.requireNonNull(months);
        months = List.copyOf(months);
    }

    public Money income() {
        return months.stream().map(MonthOfYear::income).reduce(Money.ZERO, Money::plus);
    }

    public Money expenses() {
        return months.stream().map(MonthOfYear::expenses).reduce(Money.ZERO, Money::plus);
    }

    public Money leftover() {
        return income().minus(expenses());
    }

    public Optional<BigDecimal> averageSavingsRate() {
        List<BigDecimal> rates = months.stream().flatMap(month -> month.savingsRate().stream()).toList();
        if (rates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rates.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(rates.size()), 4, RoundingMode.HALF_UP));
    }
}
