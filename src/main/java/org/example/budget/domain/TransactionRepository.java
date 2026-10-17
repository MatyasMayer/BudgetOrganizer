package org.example.budget.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistent Transactions. */
public interface TransactionRepository {

    /** Stores a new Transaction and returns it with its assigned id. */
    Transaction add(TransactionDetails details);

    Optional<Transaction> find(long id);

    /** Replaces every field of an existing Transaction. */
    void update(Transaction transaction);

    void delete(long id);

    /** Moves every Transaction of one Category to another (the merge when a Category is deleted). */
    void moveToCategory(long fromCategoryId, long toCategoryId);

    /** The Transactions matching the filter, newest date first (and newest added first within a day). */
    List<Transaction> find(TransactionFilter filter);

    /** One total per Category that has Transactions in the Month, in no particular order, without Savings-Funded
     * Expenses. */
    List<CategoryTotal> categoryTotals(Month month);

    /** One total per Category that has Transactions from {@code from} through {@code through} (both included),
     * without Savings-Funded Expenses. */
    List<CategoryTotal> categoryTotals(LocalDate from, LocalDate through);

    /** One entry per Month that has Transactions, oldest Month first, without Savings-Funded Expenses. */
    List<MonthTotals> monthTotals();

    /** One total per Category that has Savings-Funded Expenses in the Month, in no particular order. */
    List<CategoryTotal> savingsFundedTotals(Month month);

    /** The sum of every Savings-Funded Expense in any Month. */
    Money savingsFundedTotal();
}
