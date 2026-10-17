package org.example.budget.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * What the user enters for a Transaction: date, type, Category, CZK amount and an optional Note.
 * A negative Expense amount is a Refund. A blank Note is stored as no Note ({@code ""}).
 */
public record TransactionDetails(LocalDate date, TransactionType type, long categoryId, Money amount, String note) {

    public TransactionDetails {
        Objects.requireNonNull(date);
        Objects.requireNonNull(type);
        Objects.requireNonNull(amount);
        note = note == null ? "" : note.strip();
    }

    public boolean hasNote() {
        return !note.isEmpty();
    }
}
