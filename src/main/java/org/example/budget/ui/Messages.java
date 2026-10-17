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

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/** The UI text bundles: English (the base bundle) and Czech ({@code _cs}), stored as UTF-8. */
public final class Messages {

    public static final String BASE_NAME = "org.example.budget.ui.i18n.messages";

    // Without this, asking for English on a Czech system falls back to the system locale's bundle.
    private static final ResourceBundle.Control NO_SYSTEM_LOCALE_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(List.of("java.properties"));

    private Messages() {
    }

    public static ResourceBundle bundle(Locale locale) {
        return ResourceBundle.getBundle(BASE_NAME, locale, NO_SYSTEM_LOCALE_FALLBACK);
    }

    /** Bundle key of a preset Category's name, e.g. {@code category.preset.going-out}. */
    public static String presetCategoryKey(PresetCategory preset) {
        return "category.preset." + preset.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Preset Category names read from the bundle of the language they're created in. */
    public static PresetCategoryNames presetCategoryNames() {
        return (preset, language) -> bundle(language.locale()).getString(presetCategoryKey(preset));
    }

    /** Bundle key of a language's name, e.g. {@code language.cs}. */
    public static String languageKey(Language language) {
        return "language." + language.tag();
    }

    /** Bundle key of a theme's name, e.g. {@code theme.dark}. */
    public static String themeKey(Theme theme) {
        return "theme." + theme.name().toLowerCase(Locale.ROOT);
    }

    /** Bundle key of the message for a rejected Transaction, e.g. {@code transaction.error.zero-amount}. */
    public static String problemKey(InvalidTransactionException.Problem problem) {
        return "transaction.error." + problem.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Bundle key of the message for a refused Category change, e.g. {@code category.error.blank-name}. */
    public static String categoryProblemKey(InvalidCategoryException.Problem problem) {
        return "category.error." + problem.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Bundle key of the message for a rejected Recurring Template, e.g. {@code recurring.error.end-before-start}. */
    public static String recurringProblemKey(InvalidRecurringTemplateException.Problem problem) {
        return "recurring.error." + problem.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Bundle key of a Recurring Template frequency's name, e.g. {@code recurring.frequency.monthly}. */
    public static String frequencyKey(Frequency frequency) {
        return "recurring.frequency." + frequency.name().toLowerCase(Locale.ROOT);
    }

    /** Bundle key of the message for a refused Budget Limit, e.g. {@code budget-limit.error.not-positive}. */
    public static String budgetLimitProblemKey(InvalidBudgetLimitException.Problem problem) {
        return "budget-limit.error." + problem.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Bundle key of a Transaction type's name, e.g. {@code transaction.type.expense}. */
    public static String typeKey(TransactionType type) {
        return "transaction.type." + type.name().toLowerCase(Locale.ROOT);
    }
}
