package org.example.budget.domain;

class InMemoryRecurringTemplateRepositoryTest extends RecurringTemplateRepositoryContract {

    @Override
    protected Repositories createRepositories() {
        return new Repositories(new InMemoryCategoryRepository(), new InMemoryTransactionRepository(),
                new InMemoryRecurringTemplateRepository());
    }
}
