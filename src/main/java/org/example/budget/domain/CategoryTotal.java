package org.example.budget.domain;

import java.util.Objects;

/** The sum of one Category's Transactions in a Month; Refunds lower an Expense Category's total. */
public record CategoryTotal(long categoryId, TransactionType type, Money total) {

    public CategoryTotal {
        Objects.requireNonNull(type);
        Objects.requireNonNull(total);
    }
}
