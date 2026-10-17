package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The shared suite every {@link SettingsRepository} must pass. Subclass it once per implementation
 * (the in-memory fake and SQLite) so the fake used by facade tests stays faithful to the real thing.
 */
public abstract class SettingsRepositoryContract {

    private SettingsRepository repository;

    /** A fresh, empty repository for each test. */
    protected abstract SettingsRepository createRepository() throws Exception;

    @BeforeEach
    void setUp() throws Exception {
        repository = createRepository();
    }

    @Test
    void anUnknownSettingIsAbsent() {
        assertEquals(Optional.empty(), repository.find("language"));
    }

    @Test
    void aSavedSettingCanBeFound() {
        repository.save("language", "cs");

        assertEquals(Optional.of("cs"), repository.find("language"));
    }

    @Test
    void savingAgainReplacesTheValue() {
        repository.save("theme", "light");
        repository.save("theme", "dark");

        assertEquals(Optional.of("dark"), repository.find("theme"));
    }

    @Test
    void settingsAreIndependentOfEachOther() {
        repository.save("language", "en");
        repository.save("theme", "dark");

        assertEquals(Optional.of("en"), repository.find("language"));
        assertEquals(Optional.of("dark"), repository.find("theme"));
    }

    @Test
    void valuesKeepNonAsciiText() {
        repository.save("note", "Úspory – žluťoučký kůň");

        assertEquals(Optional.of("Úspory – žluťoučký kůň"), repository.find("note"));
    }
}
