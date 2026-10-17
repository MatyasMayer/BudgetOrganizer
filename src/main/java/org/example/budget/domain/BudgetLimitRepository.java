package org.example.budget.domain;

import java.util.List;
import java.util.Optional;

/** Persistent Budget Limits: per Category at most one Default Limit and at most one Limit Override per Month. */
public interface BudgetLimitRepository {

    /** Stores the limit, replacing the Category's existing one for the same Month (or its Default Limit). */
    void save(BudgetLimit limit);

    /** Removes the Category's Limit Override for the Month, or its Default Limit when the Month is empty. */
    void delete(long categoryId, Optional<Month> month);

    /** The Category's Default Limit first, then its Limit Overrides from the earliest Month. */
    List<BudgetLimit> findByCategory(long categoryId);

    /** Every Default Limit plus every Limit Override for the Month, in no particular order. */
    List<BudgetLimit> findFor(Month month);
}
