package org.example.budget.domain;

class InMemoryBudgetLimitRepositoryTest extends BudgetLimitRepositoryContract {

    @Override
    protected Repositories createRepositories() {
        return new Repositories(new InMemoryCategoryRepository(), new InMemoryBudgetLimitRepository());
    }
}
