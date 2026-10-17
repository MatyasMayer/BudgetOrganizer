package org.example.budget.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** The UI languages. The chosen one sets all UI text and the date and number formatting. */
public enum Language {
    ENGLISH("en"),
    CZECH("cs");

    private final String tag;

    Language(String tag) {
        this.tag = tag;
    }

    /** The IETF language tag, also the value stored in the settings. */
    public String tag() {
        return tag;
    }

    public Locale locale() {
        return Locale.forLanguageTag(tag);
    }

    public static Optional<Language> fromTag(String tag) {
        return Arrays.stream(values()).filter(language -> language.tag.equals(tag)).findFirst();
    }
}
