package org.example.budget.domain;

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

    /** One total per Category that has Transactions in the Month, in no particular order. */
    List<CategoryTotal> categoryTotals(Month month);
}
