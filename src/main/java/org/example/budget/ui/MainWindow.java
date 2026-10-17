package org.example.budget.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.budget.domain.BudgetService;
import org.example.budget.domain.Language;
import org.example.budget.domain.Theme;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * The application window in the chosen language and theme. Changing the language rebuilds the whole
 * UI from the new bundle; changing the theme swaps the stylesheet in place.
 */
public class MainWindow {

    private final Stage stage;
    private final BudgetService service;
    private final Path databaseFile;
    private MainController controller;
    private LocalDate today;

    public MainWindow(Stage stage, BudgetService service, Path databaseFile) {
        this.stage = stage;
        this.service = service;
        this.databaseFile = databaseFile;
    }

    /** Applies the saved theme (also for the first-launch dialog, before the window opens). */
    public static void applyTheme(Theme theme) {
        Application.setUserAgentStylesheet(theme == Theme.DARK
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
    }

    /** Opens the window in the saved language and theme, on the Dashboard. */
    public void show() {
        applyTheme(service.theme());
        today = service.today();
        load(Screen.DASHBOARD);
        stage.show();
        watchForDayChange();
    }

    /**
     * Checks every minute whether the date changed while the app is running; if so, generates the Recurring
     * Templates' newly due occurrences and reloads the shown screen (the current Month may have changed too).
     */
    private void watchForDayChange() {
        Timeline timer = new Timeline(new KeyFrame(Duration.minutes(1), event -> {
            LocalDate now = service.today();
            if (!now.equals(today)) {
                today = now;
                service.generateDueOccurrences();
                load(controller.selected());
            }
        }));
        timer.setCycleCount(Animation.INDEFINITE);
        timer.play();
    }

    public Path databaseFile() {
        return databaseFile;
    }

    public void changeLanguage(Language language) {
        if (language == service.language()) {
            return;
        }
        service.setLanguage(language);
        load(Screen.SETTINGS);
    }

    public void changeTheme(Theme theme) {
        service.setTheme(theme);
        applyTheme(theme);
    }

    private void load(Screen screen) {
        Language language = service.language();
        Locale locale = language.locale();
        // Controls that format on their own (e.g. the DatePicker's calendar) follow the default locale.
        Locale.setDefault(locale);
        ResourceBundle messages = Messages.bundle(locale);

        controller = new MainController(service, locale, type ->
                type == SettingsController.class ? new SettingsController(service, this) : null);
        FXMLLoader loader = new FXMLLoader(MainController.class.getResource("view/main.fxml"), messages);
        loader.setControllerFactory(type -> {
            if (type == MainController.class) {
                return controller;
            }
            throw new IllegalArgumentException("Unknown controller " + type);
        });
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load the main window", e);
        }

        stage.setTitle(messages.getString("app.title"));
        if (stage.getScene() == null) {
            stage.setScene(new Scene(root));
        } else {
            stage.getScene().setRoot(root);
        }
        controller.select(screen);
    }
}
