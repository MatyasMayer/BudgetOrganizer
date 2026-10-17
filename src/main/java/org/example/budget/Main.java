package org.example.budget;

import javafx.application.Application;

/**
 * Entry point. Kept separate from {@link BudgetOrganizerApp} so the app can also start from a plain
 * classpath (e.g. an IDE run configuration), where launching an {@code Application} subclass directly fails.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Application.launch(BudgetOrganizerApp.class, args);
    }
}
