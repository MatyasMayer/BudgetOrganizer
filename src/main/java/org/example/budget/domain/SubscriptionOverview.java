package org.example.budget.domain;

import java.util.List;

/**
 * What the active Subscriptions cost: each one's monthly and yearly cost in CZK, plus the grand totals.
 * {@code unpriced} lists foreign-currency ones that no ČNB Rate is known for yet, so they are not in the totals.
 */
public record SubscriptionOverview(List<Subscription> subscriptions, List<RecurringTemplate> unpriced) {

    public SubscriptionOverview {
        subscriptions = List.copyOf(subscriptions);
        unpriced = List.copyOf(unpriced);
    }

    public Money monthlyTotal() {
        return subscriptions.stream().map(Subscription::monthlyCost).reduce(Money.ZERO, Money::plus);
    }

    public Money yearlyTotal() {
        return subscriptions.stream().map(Subscription::yearlyCost).reduce(Money.ZERO, Money::plus);
    }
}
