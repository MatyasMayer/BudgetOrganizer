package org.example.budget.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** In-memory fake of {@link CategoryRepository}, kept faithful by {@code CategoryRepositoryContract}. */
public class InMemoryCategoryRepository implements CategoryRepository {

    private final List<Category> categories = new ArrayList<>();
    private long nextId = 1;

    @Override
    public Category add(String name, TransactionType type, boolean system) {
        Category category = new Category(nextId++, name, type, system);
        categories.add(category);
        return category;
    }

    @Override
    public Optional<Category> find(long id) {
        return categories.stream().filter(category -> category.id() == id).findFirst();
    }

    @Override
    public List<Category> findAll() {
        return List.copyOf(categories);
    }
}
