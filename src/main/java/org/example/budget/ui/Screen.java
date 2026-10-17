package org.example.budget.ui;

import java.net.URL;
import java.util.Locale;

/** The screens reachable from the navigation sidebar, in sidebar order. */
public enum Screen {
    DASHBOARD,
    TRANSACTIONS,
    COMPARISON,
    YEAR,
    TOP_SPENDING,
    SUBSCRIPTIONS,
    RECURRING,
    CATEGORIES,
    SETTINGS;

    private String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Bundle key of the screen's name, e.g. {@code screen.top-spending}. */
    public String titleKey() {
        return "screen." + id();
    }

    /** The screen's FXML View, e.g. {@code view/top-spending.fxml}. */
    public URL view() {
        return Screen.class.getResource("view/" + id() + ".fxml");
    }
}
