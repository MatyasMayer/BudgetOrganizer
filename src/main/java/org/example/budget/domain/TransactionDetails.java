package org.example.budget.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * What the user enters for a Transaction: date, type, Category, CZK amount and an optional Note.
 * A negative Expense amount is a Refund. A blank Note is stored as no Note ({@code ""}).
 * A foreign-currency Transaction also has a {@link Conversion}; its CZK amount is then always the
 * conversion's ({@link Conversion#czkAmount()}).
 * <p>
 * {@code savingsFunded} marks a Savings-Funded Expense: paid from Savings, so it doesn't count toward its
 * Month's Leftover or its Category's Budget Limit. Only an Expense can be Savings-Funded.
 */
public record TransactionDetails(LocalDate date, TransactionType type, long categoryId, Money amount, String note,
                                 Optional<Conversion> conversion, boolean savingsFunded) {

    public TransactionDetails {
        Objects.requireNonNull(date);
        Objects.requireNonNull(type);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(conversion);
        note = note == null ? "" : note.strip();
        conversion.ifPresent(c -> {
            if (!c.czkAmount().equals(amount)) {
                throw new IllegalArgumentException("The CZK amount " + amount + " doesn't match " + c);
            }
        });
    }

    /** A Transaction that isn't Savings-Funded. */
    public TransactionDetails(LocalDate date, TransactionType type, long categoryId, Money amount, String note,
                              Optional<Conversion> conversion) {
        this(date, type, categoryId, amount, note, conversion, false);
    }

    /** A Transaction entered in CZK. */
    public TransactionDetails(LocalDate date, TransactionType type, long categoryId, Money amount, String note) {
        this(date, type, categoryId, amount, note, Optional.empty());
    }

    /** A Transaction entered in a foreign currency, counting with the conversion's CZK amount. */
    public static TransactionDetails converted(LocalDate date, TransactionType type, long categoryId,
                                               Conversion conversion, String note) {
        return new TransactionDetails(date, type, categoryId, conversion.czkAmount(), note, Optional.of(conversion));
    }

    /** The same Transaction, marked as a Savings-Funded Expense or not. */
    public TransactionDetails withSavingsFunded(boolean savingsFunded) {
        return new TransactionDetails(date, type, categoryId, amount, note, conversion, savingsFunded);
    }

    public boolean hasNote() {
        return !note.isEmpty();
    }

    /** The currency the amount was entered in: CZK, or the Original Amount's currency. */
    public Currency currency() {
        return conversion.map(c -> c.originalAmount().currency()).orElse(Currency.CZK);
    }
}
