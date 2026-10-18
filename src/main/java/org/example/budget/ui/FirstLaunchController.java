package org.example.budget.ui;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import org.example.budget.domain.Language;
import org.example.budget.domain.Money;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * The dialog shown once on a new database. It asks for the UI language, and its own text switches
 * to the language as soon as it's picked, and for the Opening Savings (empty means zero).
 */
public class FirstLaunchController {

    /** What the user chose in the dialog. */
    public record Choice(Language language, Money openingSavings) {
    }

    private final Language suggested;
    private Dialog<ButtonType> dialog;
    private ButtonType start;

    @FXML
    private ToggleGroup languageGroup;
    @FXML
    private RadioButton english;
    @FXML
    private RadioButton czech;
    @FXML
    private Label languagePrompt;
    @FXML
    private Label openingSavingsPrompt;
    @FXML
    private TextField openingSavings;
    @FXML
    private Label openingSavingsHint;

    private FirstLaunchController(Language suggested) {
        this.suggested = suggested;
    }

    /** Opens the dialog; the user's choice, or empty when the dialog was closed. */
    public static Optional<Choice> open() {
        FirstLaunchController controller = new FirstLaunchController(suggestedLanguage());
        FXMLLoader loader = new FXMLLoader(FirstLaunchController.class.getResource("view/first-launch.fxml"),
                Messages.bundle(controller.suggested.locale()));
        loader.setController(controller);
        Parent content;
        try {
            content = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load the first-launch dialog", e);
        }

        controller.dialog = new Dialog<>();
        controller.start = new ButtonType("", ButtonBar.ButtonData.OK_DONE);
        controller.dialog.getDialogPane().getButtonTypes().addAll(controller.start, ButtonType.CLOSE);
        controller.dialog.getDialogPane().lookupButton(ButtonType.CLOSE).setVisible(false);
        controller.dialog.getDialogPane().setContent(content);
        controller.translate();
        controller.dialog.getDialogPane().lookupButton(controller.start).disableProperty().bind(
                Bindings.createBooleanBinding(
                        () -> controller.chosenOpeningSavings().isEmpty(), controller.openingSavings.textProperty(),
                        controller.languageGroup.selectedToggleProperty()));
        return controller.dialog.showAndWait()
                .filter(controller.start::equals)
                .map(button -> new Choice(controller.chosen(), controller.chosenOpeningSavings().orElseThrow()));
    }

    /** Czech on a Czech system, otherwise English. */
    private static Language suggestedLanguage() {
        return Locale.getDefault().getLanguage().equals(Language.CZECH.tag()) ? Language.CZECH : Language.ENGLISH;
    }

    @FXML
    private void initialize() {
        english.setUserData(Language.ENGLISH);
        czech.setUserData(Language.CZECH);
        (suggested == Language.CZECH ? czech : english).setSelected(true);
        languageGroup.selectedToggleProperty().addListener((obs, previous, selected) -> {
            if (selected == null) {
                previous.setSelected(true);
            } else {
                translate();
            }
        });
    }

    private Language chosen() {
        return (Language) languageGroup.getSelectedToggle().getUserData();
    }

    /** The Opening Savings typed in the chosen language's format; zero when empty, absent when not valid. */
    private Optional<Money> chosenOpeningSavings() {
        String text = openingSavings.getText();
        if (text == null || text.isBlank()) {
            return Optional.of(Money.ZERO);
        }
        return Money.parse(text, chosen().locale()).filter(amount -> amount.halere() >= 0);
    }

    private void translate() {
        ResourceBundle messages = Messages.bundle(chosen().locale());
        english.setText(messages.getString(Messages.languageKey(Language.ENGLISH)));
        czech.setText(messages.getString(Messages.languageKey(Language.CZECH)));
        languagePrompt.setText(messages.getString("first-launch.language"));
        openingSavingsPrompt.setText(messages.getString("first-launch.opening-savings"));
        openingSavings.setPromptText(Money.ZERO.formatAmount(chosen().locale()));
        openingSavingsHint.setText(messages.getString("first-launch.opening-savings.hint"));
        if (dialog != null) {
            dialog.setTitle(messages.getString("first-launch.title"));
            dialog.setHeaderText(messages.getString("first-launch.header"));
            ((Button) dialog.getDialogPane().lookupButton(start)).setText(messages.getString("first-launch.start"));
        }
    }
}
