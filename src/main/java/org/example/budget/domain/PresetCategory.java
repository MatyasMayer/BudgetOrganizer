package org.example.budget.domain;

/**
 * The Categories created on first start, in this order. Their names come from the language chosen then;
 * afterwards they're ordinary user-editable Categories.
 */
public enum PresetCategory {
    FOOD(TransactionType.EXPENSE),
    RENT(TransactionType.EXPENSE),
    TRANSPORT(TransactionType.EXPENSE),
    GAMES(TransactionType.EXPENSE),
    SUBSCRIPTIONS(TransactionType.EXPENSE),
    GOING_OUT(TransactionType.EXPENSE),
    ELECTRONICS(TransactionType.EXPENSE),
    HEALTH(TransactionType.EXPENSE),
    CLOTHES(TransactionType.EXPENSE),
    GIFTS(TransactionType.EXPENSE),
    OTHER_EXPENSE(TransactionType.EXPENSE),
    SALARY(TransactionType.INCOME),
    GIFT(TransactionType.INCOME),
    OTHER_INCOME(TransactionType.INCOME),
    FROM_SAVINGS(TransactionType.INCOME);

    private final TransactionType type;

    PresetCategory(TransactionType type) {
        this.type = type;
    }

    public TransactionType type() {
        return type;
    }

    /** From Savings is the system Category the Savings rules rely on. */
    public boolean system() {
        return this == FROM_SAVINGS;
    }
}
