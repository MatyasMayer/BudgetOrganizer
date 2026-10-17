package org.example.budget.domain;

import java.time.LocalDate;
import java.util.Objects;

/** A single recorded Income or Expense, with exactly one Category. */
public record Transaction(long id, TransactionDetails details) {

    public Transaction {
        Objects.requireNonNull(details);
    }

    public LocalDate date() {
        return details.date();
    }

    public TransactionType type() {
        return details.type();
    }

    public long categoryId() {
        return details.categoryId();
    }

    public Money amount() {
        return details.amount();
    }

    public String note() {
        return details.note();
    }

    /** A Refund is an Expense with a negative amount. */
    public boolean isRefund() {
        return type() == TransactionType.EXPENSE && amount().halere() < 0;
    }
}
