package org.example.budget.ui;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.example.budget.domain.BudgetService;
import org.example.budget.domain.Category;
import org.example.budget.domain.InvalidTransactionException;
import org.example.budget.domain.Money;
import org.example.budget.domain.Transaction;
import org.example.budget.domain.TransactionDetails;
import org.example.budget.domain.TransactionType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * The Transaction editor dialog: creates a new Transaction (date defaulting to today) or edits an existing
 * one. Only Categories of the chosen type are offered. Saving goes through the service, which validates.
 */
public class TransactionEditorController {

    private final BudgetService service;
    private final Locale locale;
    private final Optional<Transaction> existing;

    @FXML
    private ResourceBundle resources;
    @FXML
    private ToggleGroup typeGroup;
    @FXML
    private RadioButton expenseType;
    @FXML
    private RadioButton incomeType;
    @FXML
    private DatePicker date;
    @FXML
    private TextField amount;
    @FXML
    private ComboBox<Category> category;
    @FXML
    private TextField note;
    @FXML
    private Label error;

    private TransactionEditorController(BudgetService service, Locale locale, Optional<Transaction> existing) {
        this.service = service;
        this.locale = locale;
        this.existing = existing;
    }

    /** Opens the dialog; true when a Transaction was saved. */
    public static boolean open(Window owner, BudgetService service, ResourceBundle resources, Locale locale,
                               Optional<Transaction> existing) {
        TransactionEditorController controller = new TransactionEditorController(service, locale, existing);
        FXMLLoader loader = new FXMLLoader(
                TransactionEditorController.class.getResource("view/transaction-editor.fxml"), resources);
        loader.setControllerFactory(type -> controller);
        Parent content;
        try {
            content = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load the Transaction editor", e);
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(resources.getString(existing.isPresent() ? "editor.title.edit" : "editor.title.new"));
        ButtonType save = new ButtonType(resources.getString("editor.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(content);
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (!controller.save()) {
                event.consume();
            }
        });
        dialog.setOnShown(event -> controller.amount.requestFocus());
        return dialog.showAndWait().filter(save::equals).isPresent();
    }

    @FXML
    private void initialize() {
        expenseType.setUserData(TransactionType.EXPENSE);
        incomeType.setUserData(TransactionType.INCOME);
        date.setConverter(new LocalizedDateConverter(locale));
        category.setCellFactory(list -> new CategoryCell());
        category.setButtonCell(new CategoryCell());
        typeGroup.selectedToggleProperty().addListener((obs, previous, selected) -> offerCategoriesOf(type()));

        existing.ifPresentOrElse(this::fill, () -> {
            date.setValue(service.today());
            expenseType.setSelected(true);
        });
    }

    private void fill(Transaction transaction) {
        (transaction.type() == TransactionType.INCOME ? incomeType : expenseType).setSelected(true);
        date.setValue(transaction.date());
        amount.setText(transaction.amount().formatAmount(locale));
        category.getItems().stream()
                .filter(c -> c.id() == transaction.categoryId())
                .findFirst()
                .ifPresent(category.getSelectionModel()::select);
        note.setText(transaction.note());
    }

    private TransactionType type() {
        return (TransactionType) typeGroup.getSelectedToggle().getUserData();
    }

    private void offerCategoriesOf(TransactionType type) {
        Category previous = category.getValue();
        category.getItems().setAll(service.categories(type));
        if (previous != null && previous.type() == type) {
            category.setValue(previous);
        } else if (!category.getItems().isEmpty()) {
            category.getSelectionModel().selectFirst();
        }
    }

    /** Validates and stores the input; false (with a message shown) keeps the dialog open. */
    private boolean save() {
        Optional<LocalDate> chosenDate = typedDate();
        if (chosenDate.isEmpty()) {
            return fail(resources.getString("editor.error.date"));
        }
        Optional<Money> chosenAmount = Money.parse(amount.getText(), locale);
        if (chosenAmount.isEmpty()) {
            return fail(MessageFormat.format(resources.getString("editor.error.amount"),
                    Money.ofHalere(-15_050).formatAmount(locale)));
        }
        if (category.getValue() == null) {
            return fail(resources.getString("transaction.error.unknown-category"));
        }
        TransactionDetails details = new TransactionDetails(
                chosenDate.get(), type(), category.getValue().id(), chosenAmount.get(), note.getText());
        try {
            existing.ifPresentOrElse(
                    transaction -> service.editTransaction(transaction.id(), details),
                    () -> service.recordTransaction(details));
            return true;
        } catch (InvalidTransactionException e) {
            return fail(resources.getString(Messages.problemKey(e.problem())));
        }
    }

    /** The date as typed, even if the picker hasn't committed the text yet. */
    private Optional<LocalDate> typedDate() {
        String text = date.getEditor().getText();
        if (text == null || text.isBlank()) {
            return Optional.ofNullable(date.getValue());
        }
        try {
            return Optional.of(date.getConverter().fromString(text));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private boolean fail(String message) {
        error.setText(message);
        return false;
    }

    private static final class CategoryCell extends ListCell<Category> {
        @Override
        protected void updateItem(Category item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.name());
        }
    }

    /** Shows and reads dates in the UI language, e.g. "17. 10. 2026" in Czech. */
    private static final class LocalizedDateConverter extends StringConverter<LocalDate> {
        private final DateTimeFormatter format;

        LocalizedDateConverter(Locale locale) {
            format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
        }

        @Override
        public String toString(LocalDate value) {
            return value == null ? "" : format.format(value);
        }

        @Override
        public LocalDate fromString(String text) {
            return text == null || text.isBlank() ? null : LocalDate.parse(text.strip(), format);
        }
    }
}
