package org.example.budget.domain;

import java.util.Optional;

/** Persistent key/value application settings. */
public interface SettingsRepository {

    Optional<String> find(String key);

    /** Inserts the setting or replaces its current value. */
    void save(String key, String value);
}
