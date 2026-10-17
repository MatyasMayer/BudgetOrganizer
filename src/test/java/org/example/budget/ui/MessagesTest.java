package org.example.budget.ui;

import org.junit.jupiter.api.Test;

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
