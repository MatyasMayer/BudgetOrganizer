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

    /** The Transactions matching the filter, newest date first (and newest added first within a day). */
    List<Transaction> find(TransactionFilter filter);
}
