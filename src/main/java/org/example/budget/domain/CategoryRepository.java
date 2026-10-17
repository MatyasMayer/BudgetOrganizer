package org.example.budget.domain;

import java.util.List;
import java.util.Optional;

/** Persistent Categories. */
public interface CategoryRepository {

    /** Stores a new Category and returns it with its assigned id. */
    Category add(String name, TransactionType type, boolean system);

    Optional<Category> find(long id);

    /** All Categories in the order they were added. */
    List<Category> findAll();

    void rename(long id, String name);

    /** Removes the Category; its Transactions must have been moved elsewhere first. */
    void delete(long id);
}
