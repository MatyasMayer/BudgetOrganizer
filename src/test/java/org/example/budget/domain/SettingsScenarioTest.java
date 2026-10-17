package org.example.budget.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Facade-level scenarios for the first launch and the language and theme Settings. */
class SettingsScenarioTest {

    private static final PresetCategoryNames NAMES = (preset, language) -> language.tag() + ":" + preset.name();

    private final InMemorySettingsRepository settings = new InMemorySettingsRepository();
    private final InMemoryCategoryRepository categories = new InMemoryCategoryRepository();
    private final BudgetService service = startApp();

    private BudgetService startApp() {
        return new BudgetService(settings, categories, new InMemoryTransactionRepository(),
                Clock.fixed(LocalDate.of(2026, 10, 17)));
    }

    @Test
    void aNewDatabaseNeedsTheFirstLaunch() {
        assertTrue(service.isFirstLaunch());
        assertTrue(service.categories(TransactionType.EXPENSE).isEmpty());
    }

    @Test
    void theFirstLaunchCreatesThePresetCategoriesInTheChosenLanguage() {
        service.completeFirstLaunch(Language.CZECH, NAMES);

        assertEquals("cs:FOOD", service.categories(TransactionType.EXPENSE).getFirst().name());
        assertEquals(List.of("cs:SALARY", "cs:GIFT", "cs:OTHER_INCOME", "cs:FROM_SAVINGS"),
                service.categories(TransactionType.INCOME).stream().map(Category::name).toList());
        assertEquals(Language.CZECH, service.language());
    }

    @Test
    void theFirstLaunchHappensOnlyOnce() {
        service.completeFirstLaunch(Language.ENGLISH, NAMES);

        BudgetService afterRestart = startApp();

        assertFalse(afterRestart.isFirstLaunch());
        assertEquals(Language.ENGLISH, afterRestart.language());
        assertEquals(11, afterRestart.categories(TransactionType.EXPENSE).size());
    }

    @Test
    void theFirstLaunchDoesNotDuplicateExistingCategories() {
        service.seedPresetCategories(PresetCategory::name);

        service.completeFirstLaunch(Language.CZECH, NAMES);

        assertEquals("FOOD", service.categories(TransactionType.EXPENSE).getFirst().name());
        assertEquals(4, service.categories(TransactionType.INCOME).size());
    }

    @Test
    void theLanguageIsEnglishUntilChosen() {
        assertEquals(Language.ENGLISH, service.language());
    }

    @Test
    void aChangedLanguageIsRememberedAcrossRestarts() {
        service.completeFirstLaunch(Language.ENGLISH, NAMES);

        service.setLanguage(Language.CZECH);

        assertEquals(Language.CZECH, startApp().language());
    }

    @Test
    void changingTheLanguageLaterKeepsTheCategoryNames() {
        service.completeFirstLaunch(Language.ENGLISH, NAMES);

        service.setLanguage(Language.CZECH);

        assertEquals("en:FOOD", service.categories(TransactionType.EXPENSE).getFirst().name());
    }

    @Test
    void theThemeIsLightUntilChosen() {
        assertEquals(Theme.LIGHT, service.theme());
    }

    @Test
    void aChangedThemeIsRememberedAcrossRestarts() {
        service.setTheme(Theme.DARK);

        assertEquals(Theme.DARK, startApp().theme());

        service.setTheme(Theme.LIGHT);

        assertEquals(Theme.LIGHT, startApp().theme());
    }

    @Test
    void everyLanguageHasADistinctTag() {
        assertEquals(List.of("en", "cs"), List.of(Language.ENGLISH.tag(), Language.CZECH.tag()));
        assertEquals(Language.CZECH, Language.fromTag("cs").orElseThrow());
        assertTrue(Language.fromTag("de").isEmpty());
    }
}
