package org.example.budget.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** In-memory fake of {@link BudgetLimitRepository}, kept faithful by {@code BudgetLimitRepositoryContract}. */
public class InMemoryBudgetLimitRepository implements BudgetLimitRepository {

    private final List<BudgetLimit> limits = new ArrayList<>();

    @Override
    public void save(BudgetLimit limit) {
        delete(limit.categoryId(), limit.month());
        limits.add(limit);
    }

    @Override
    public void delete(long categoryId, Optional<Month> month) {
        limits.removeIf(limit -> limit.categoryId() == categoryId && limit.month().equals(month));
    }

    @Override
    public List<BudgetLimit> findByCategory(long categoryId) {
        return limits.stream()
                .filter(limit -> limit.categoryId() == categoryId)
                .sorted(Comparator.comparing((BudgetLimit limit) -> limit.month().orElse(null),
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    @Override
    public List<BudgetLimit> findFor(Month month) {
        return limits.stream()
                .filter(limit -> limit.month().map(month::equals).orElse(true))
                .toList();
    }
}
