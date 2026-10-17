package org.example.budget.domain;

class InMemoryCategoryRepositoryTest extends CategoryRepositoryContract {

    @Override
    protected CategoryRepository createRepository() {
        return new InMemoryCategoryRepository();
    }
}
