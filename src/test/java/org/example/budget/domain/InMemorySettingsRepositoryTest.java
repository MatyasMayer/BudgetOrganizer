package org.example.budget.domain;

class InMemorySettingsRepositoryTest extends SettingsRepositoryContract {

    @Override
    protected SettingsRepository createRepository() {
        return new InMemorySettingsRepository();
    }
}
