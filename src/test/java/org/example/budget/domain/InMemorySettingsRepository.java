package org.example.budget.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory fake of {@link SettingsRepository}, kept faithful by {@code SettingsRepositoryContract}. */
public class InMemorySettingsRepository implements SettingsRepository {

    private final Map<String, String> values = new HashMap<>();

    @Override
    public Optional<String> find(String key) {
        return Optional.ofNullable(values.get(key));
    }

    @Override
    public void save(String key, String value) {
        values.put(key, value);
    }
}
