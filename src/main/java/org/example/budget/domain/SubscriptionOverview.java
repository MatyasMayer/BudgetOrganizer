package org.example.budget.domain;

import java.util.List;

/**
 * What the active Subscriptions cost: each one's monthly and yearly cost in CZK, plus the grand totals.
 */
public record SubscriptionOverview(List<Subscription> subscriptions) {

    public SubscriptionOverview {
        subscriptions = List.copyOf(subscriptions);
    }

    public Money monthlyTotal() {
        return subscriptions.stream().map(Subscription::monthlyCost).reduce(Money.ZERO, Money::plus);
    }

    public Money yearlyTotal() {
        return subscriptions.stream().map(Subscription::yearlyCost).reduce(Money.ZERO, Money::plus);
    }
}
