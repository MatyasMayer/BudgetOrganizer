package org.example.budget.domain;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory fake of {@link TransactionRepository}, kept faithful by {@code TransactionRepositoryContract}. */
public class InMemoryTransactionRepository implements TransactionRepository {

    private static final Comparator<Transaction> NEWEST_FIRST = Comparator
            .comparing(Transaction::date)
            .thenComparingLong(Transaction::id)
            .reversed();

    private final Map<Long, Transaction> transactions = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public Transaction add(TransactionDetails details) {
        Transaction transaction = new Transaction(nextId++, details);
        transactions.put(transaction.id(), transaction);
        return transaction;
    }

    @Override
    public Optional<Transaction> find(long id) {
        return Optional.ofNullable(transactions.get(id));
    }

    @Override
    public void update(Transaction transaction) {
        transactions.replace(transaction.id(), transaction);
    }

    @Override
    public void delete(long id) {
        transactions.remove(id);
    }

    @Override
    public void moveToCategory(long fromCategoryId, long toCategoryId) {
        transactions.replaceAll((id, transaction) -> {
            if (transaction.categoryId() != fromCategoryId) {
                return transaction;
            }
            TransactionDetails d = transaction.details();
            return new Transaction(id, new TransactionDetails(d.date(), d.type(), toCategoryId, d.amount(), d.note()));
        });
    }

    @Override
    public List<Transaction> find(TransactionFilter filter) {
        return transactions.values().stream()
                .filter(filter::matches)
                .sorted(NEWEST_FIRST)
                .toList();
    }
}
