package org.example.budget.ui;

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
}
