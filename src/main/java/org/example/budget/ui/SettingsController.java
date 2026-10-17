package org.example.budget.ui;

import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import org.example.budget.domain.BudgetService;
import org.example.budget.domain.Language;
import org.example.budget.domain.Theme;

import java.util.ResourceBundle;

/** The Settings screen: UI language, theme, and where {@code budget.db} is stored. */
public class SettingsController {

    private final BudgetService service;
    private final MainWindow window;

    @FXML
    private ResourceBundle resources;
    @FXML
    private ToggleGroup languageGroup;
    @FXML
    private RadioButton english;
    @FXML
    private RadioButton czech;
    @FXML
    private ToggleGroup themeGroup;
    @FXML
    private RadioButton light;
    @FXML
    private RadioButton dark;
    @FXML
    private TextField databaseFile;

    public SettingsController(BudgetService service, MainWindow window) {
        this.service = service;
        this.window = window;
    }

    @FXML
    private void initialize() {
        english.setUserData(Language.ENGLISH);
        czech.setUserData(Language.CZECH);
        english.setText(resources.getString(Messages.languageKey(Language.ENGLISH)));
        czech.setText(resources.getString(Messages.languageKey(Language.CZECH)));
        (service.language() == Language.CZECH ? czech : english).setSelected(true);

        light.setUserData(Theme.LIGHT);
        dark.setUserData(Theme.DARK);
        light.setText(resources.getString(Messages.themeKey(Theme.LIGHT)));
        dark.setText(resources.getString(Messages.themeKey(Theme.DARK)));
        (service.theme() == Theme.DARK ? dark : light).setSelected(true);

        databaseFile.setText(window.databaseFile().toAbsolutePath().toString());

        // Listeners go last so selecting the saved values above doesn't count as a change.
        languageGroup.selectedToggleProperty().addListener((obs, previous, selected) -> {
            if (selected != null) {
                window.changeLanguage((Language) selected.getUserData());
            }
        });
        themeGroup.selectedToggleProperty().addListener((obs, previous, selected) -> {
            if (selected != null) {
                window.changeTheme((Theme) selected.getUserData());
            }
        });
    }
}
