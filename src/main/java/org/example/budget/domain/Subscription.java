package org.example.budget.domain;

import java.util.Objects;

/** An active Subscription (Expense Recurring Template) with what it costs in CZK per month and per year. */
public record Subscription(RecurringTemplate template, Money monthlyCost, Money yearlyCost) {

    public Subscription {
        Objects.requireNonNull(template);
        Objects.requireNonNull(monthlyCost);
        Objects.requireNonNull(yearlyCost);
    }
}
