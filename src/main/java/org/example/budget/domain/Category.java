package org.example.budget.domain;

import java.util.Objects;

/**
 * A user-editable label grouping Transactions of one type. A system Category (From Savings) is needed
 * by the Savings rules and can't be deleted or merged away.
 */
public record Category(long id, String name, TransactionType type, boolean system) {

    public Category {
        Objects.requireNonNull(name);
        Objects.requireNonNull(type);
    }
}
