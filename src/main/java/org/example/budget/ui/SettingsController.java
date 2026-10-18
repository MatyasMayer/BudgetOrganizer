package org.example.budget.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.stage.DirectoryChooser;
import org.example.budget.domain.BudgetService;
import org.example.budget.domain.Language;
import org.example.budget.domain.Money;
import org.example.budget.domain.Theme;

import java.io.File;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

/** The Settings screen: UI language, theme, Opening Savings, and where {@code budget.db} is stored. */
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
    @FXML
    private TextField openingSavings;
    @FXML
    private Label openingSavingsStatus;
    @FXML
    private Label backupStatus;

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
        openingSavings.setText(service.openingSavings().formatAmount(locale()));

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

    @FXML
    private void saveOpeningSavings() {
        Optional<Money> amount = Money.parse(openingSavings.getText(), locale()).filter(money -> money.halere() >= 0);
        if (amount.isEmpty()) {
            openingSavingsStatus.getStyleClass().setAll("label", "danger");
            openingSavingsStatus.setText(MessageFormat.format(resources.getString("settings.opening-savings.error"),
                    Money.ofHalere(5_000_000).formatAmount(locale())));
            return;
        }
        service.setOpeningSavings(amount.get());
        openingSavings.setText(amount.get().formatAmount(locale()));
        openingSavingsStatus.getStyleClass().setAll("label", "success");
        openingSavingsStatus.setText(resources.getString("settings.opening-savings.saved"));
    }

    @FXML
    private void backupDatabase() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(resources.getString("settings.backup.choose"));
        File folder = chooser.showDialog(databaseFile.getScene().getWindow());
        if (folder == null) {
            return;
        }
        try {
            Path backup = window.backup().to(folder.toPath());
            backupStatus.getStyleClass().setAll("label", "success");
            backupStatus.setText(MessageFormat.format(resources.getString("settings.backup.done"), backup));
        } catch (RuntimeException e) {
            backupStatus.getStyleClass().setAll("label", "danger");
            backupStatus.setText(MessageFormat.format(resources.getString("settings.backup.error"), e.getMessage()));
        }
    }

    private Locale locale() {
        return service.language().locale();
    }
}
