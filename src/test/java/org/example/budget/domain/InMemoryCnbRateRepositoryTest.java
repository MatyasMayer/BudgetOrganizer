package org.example.budget.domain;

class InMemoryCnbRateRepositoryTest extends CnbRateRepositoryContract {

    @Override
    protected CnbRateRepository createRepository() {
        return new InMemoryCnbRateRepository();
    }
}
