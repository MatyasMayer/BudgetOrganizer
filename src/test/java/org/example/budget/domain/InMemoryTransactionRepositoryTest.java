package org.example.budget.domain;

class InMemoryTransactionRepositoryTest extends TransactionRepositoryContract {

    @Override
    protected Repositories createRepositories() {
        return new Repositories(new InMemoryCategoryRepository(), new InMemoryTransactionRepository());
    }
}
