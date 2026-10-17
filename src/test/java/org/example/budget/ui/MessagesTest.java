package org.example.budget.ui;

import org.example.budget.domain.Frequency;
import org.example.budget.domain.InvalidBudgetLimitException;
import org.example.budget.domain.InvalidCategoryException;
import org.example.budget.domain.InvalidRecurringTemplateException;
import org.example.budget.domain.InvalidTransactionException;
import org.example.budget.domain.Language;
import org.example.budget.domain.PresetCategory;
import org.example.budget.domain.PresetCategoryNames;
import org.example.budget.domain.Theme;
import org.example.budget.domain.TransactionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessagesTest {

    @Test
    void czechAndEnglishDefineTheSameKeys() {
        Set<String> english = Messages.bundle(Locale.ENGLISH).keySet();
        Set<String> czech = Messages.bundle(Locale.forLanguageTag("cs")).keySet();

        assertEquals(english, czech);
    }

    @Test
    void everyScreenHasANameInBothLanguages() {
        for (Screen screen : Screen.values()) {
            Messages.bundle(Locale.ENGLISH).getString(screen.titleKey());
            Messages.bundle(Locale.forLanguageTag("cs")).getString(screen.titleKey());
        }
    }

    @Test
    void everyPresetCategoryAndTransactionTypeHasANameInBothLanguages() {
        for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("cs"))) {
            ResourceBundle bundle = Messages.bundle(locale);
            for (PresetCategory preset : PresetCategory.values()) {
                bundle.getString(Messages.presetCategoryKey(preset));
            }
            for (TransactionType type : TransactionType.values()) {
                bundle.getString(Messages.typeKey(type));
            }
            for (InvalidTransactionException.Problem problem : InvalidTransactionException.Problem.values()) {
                bundle.getString(Messages.problemKey(problem));
            }
        }
        assertEquals("Z úspor", Messages.bundle(Locale.forLanguageTag("cs"))
                .getString(Messages.presetCategoryKey(PresetCategory.FROM_SAVINGS)));
    }

    @Test
    void presetCategoryNamesComeFromTheChosenLanguage() {
        PresetCategoryNames names = Messages.presetCategoryNames();

        assertEquals("Z úspor", names.name(PresetCategory.FROM_SAVINGS, Language.CZECH));
        assertEquals("From Savings", names.name(PresetCategory.FROM_SAVINGS, Language.ENGLISH));
    }

    @Test
    void everyLanguageAndThemeHasANameInBothLanguages() {
        for (Language bundleLanguage : Language.values()) {
            ResourceBundle bundle = Messages.bundle(bundleLanguage.locale());
            for (Language language : Language.values()) {
                bundle.getString(Messages.languageKey(language));
            }
            for (Theme theme : Theme.values()) {
                bundle.getString(Messages.themeKey(theme));
            }
        }
    }

    @Test
    void everyRefusedCategoryChangeHasAMessageInBothLanguages() {
        for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("cs"))) {
            for (InvalidCategoryException.Problem problem : InvalidCategoryException.Problem.values()) {
                Messages.bundle(locale).getString(Messages.categoryProblemKey(problem));
            }
        }
    }

    @Test
    void everyFrequencyAndRefusedRecurringTemplateHasTextInBothLanguages() {
        for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("cs"))) {
            for (Frequency frequency : Frequency.values()) {
                Messages.bundle(locale).getString(Messages.frequencyKey(frequency));
            }
            for (InvalidRecurringTemplateException.Problem problem : InvalidRecurringTemplateException.Problem.values()) {
                Messages.bundle(locale).getString(Messages.recurringProblemKey(problem));
            }
        }
    }

    @Test
    void everyRefusedBudgetLimitHasAMessageInBothLanguages() {
        for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("cs"))) {
            for (InvalidBudgetLimitException.Problem problem : InvalidBudgetLimitException.Problem.values()) {
                Messages.bundle(locale).getString(Messages.budgetLimitProblemKey(problem));
            }
        }
    }

    @Test
    void englishIsUsedEvenOnACzechSystem() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("cs-CZ"));
            ResourceBundle bundle = Messages.bundle(Locale.ENGLISH);

            assertEquals("Settings", bundle.getString("screen.settings"));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void czechTextIsReadAsUtf8() {
        assertEquals("Nastavení", Messages.bundle(Locale.forLanguageTag("cs-CZ")).getString("screen.settings"));
    }
}
