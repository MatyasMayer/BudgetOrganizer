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
import org.example.budget.domain.Currency;
import org.example.budget.domain.Frequency;
import org.example.budget.domain.InvalidRecurringTemplateException;
import org.example.budget.domain.Money;
import org.example.budget.domain.OriginalAmount;
import org.example.budget.domain.RecurringTemplateDetails;
import org.example.budget.domain.TransactionType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * The dialog that creates a Recurring Template in CZK or EUR. Only Categories of the chosen type are offered; the
 * day of month and start date default to today. Saving goes through the service, which validates.
 */
public class RecurringTemplateEditorController {

    private final BudgetService service;
    private final Locale locale;

    @FXML
    private ResourceBundle resources;
    @FXML
    private ToggleGroup typeGroup;
    @FXML
    private RadioButton expenseType;
    @FXML
    private RadioButton incomeType;
    @FXML
    private TextField amount;
    @FXML
    private ComboBox<Currency> currency;
    @FXML
    private Label foreignCurrencyHint;
    @FXML
    private ComboBox<Category> category;
    @FXML
    private TextField note;
    @FXML
    private ComboBox<Frequency> frequency;
    @FXML
    private TextField day;
    @FXML
    private DatePicker startDate;
    @FXML
    private DatePicker endDate;
    @FXML
    private Label error;

    private RecurringTemplateEditorController(BudgetService service, Locale locale) {
        this.service = service;
        this.locale = locale;
    }

    /** Opens the dialog; true when a template was created. */
    public static boolean open(Window owner, BudgetService service, ResourceBundle resources, Locale locale) {
        RecurringTemplateEditorController controller = new RecurringTemplateEditorController(service, locale);
        FXMLLoader loader = new FXMLLoader(
                RecurringTemplateEditorController.class.getResource("view/recurring-template-editor.fxml"),
                resources);
        loader.setController(controller);
        Parent content;
        try {
            content = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load the Recurring Template editor", e);
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(resources.getString("recurring.editor.title"));
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
        category.setCellFactory(list -> new CategoryCell());
        category.setButtonCell(new CategoryCell());
        typeGroup.selectedToggleProperty().addListener((obs, previous, selected) -> offerCategoriesOf(type()));
        currency.getItems().setAll(Currency.values());
        currency.setConverter(new CurrencyConverter());
        foreignCurrencyHint.managedProperty().bind(foreignCurrencyHint.visibleProperty());
        currency.valueProperty().addListener((obs, previous, value) ->
                foreignCurrencyHint.setVisible(value != null && !value.isBaseCurrency()));
        currency.setValue(Currency.CZK);
        foreignCurrencyHint.setVisible(false);
        frequency.getItems().setAll(Frequency.values());
        frequency.setConverter(new FrequencyConverter());
        startDate.setConverter(new TransactionEditorController.LocalizedDateConverter(locale));
        endDate.setConverter(new TransactionEditorController.LocalizedDateConverter(locale));

        LocalDate today = service.today();
        expenseType.setSelected(true);
        frequency.setValue(Frequency.MONTHLY);
        day.setText(String.valueOf(today.getDayOfMonth()));
        startDate.setValue(today);
    }

    private TransactionType type() {
        return (TransactionType) typeGroup.getSelectedToggle().getUserData();
    }

    private void offerCategoriesOf(TransactionType type) {
        category.getItems().setAll(service.categories(type));
        if (!category.getItems().isEmpty()) {
            category.getSelectionModel().selectFirst();
        }
    }

    /** Validates and stores the input; false (with a message shown) keeps the dialog open. */
    private boolean save() {
        Optional<Money> chosenAmount = Money.parse(amount.getText(), locale);
        if (chosenAmount.isEmpty()) {
            return fail(MessageFormat.format(resources.getString("recurring.editor.error.amount"),
                    Money.ofHalere(1_250_000).formatAmount(locale)));
        }
        if (category.getValue() == null) {
            return fail(resources.getString(
                    Messages.recurringProblemKey(InvalidRecurringTemplateException.Problem.UNKNOWN_CATEGORY)));
        }
        Optional<Integer> chosenDay = typedDay();
        if (chosenDay.isEmpty()) {
            return fail(resources.getString(
                    Messages.recurringProblemKey(InvalidRecurringTemplateException.Problem.DAY_OUT_OF_RANGE)));
        }
        Optional<Optional<LocalDate>> start = typedDate(startDate);
        if (start.isEmpty() || start.get().isEmpty()) {
            return fail(resources.getString("editor.error.date"));
        }
        Optional<Optional<LocalDate>> end = typedDate(endDate);
        if (end.isEmpty()) {
            return fail(resources.getString("editor.error.date"));
        }
        Currency chosenCurrency = currency.getValue();
        RecurringTemplateDetails details = chosenCurrency.isBaseCurrency()
                ? new RecurringTemplateDetails(type(), category.getValue().id(), chosenAmount.get(),
                        note.getText(), frequency.getValue(), chosenDay.get(), start.get().get(), end.get())
                : RecurringTemplateDetails.inForeignCurrency(type(), category.getValue().id(),
                        new OriginalAmount(chosenCurrency, chosenAmount.get().halere()), note.getText(),
                        frequency.getValue(), chosenDay.get(), start.get().get(), end.get());
        try {
            service.createRecurringTemplate(details);
            return true;
        } catch (InvalidRecurringTemplateException e) {
            return fail(resources.getString(Messages.recurringProblemKey(e.problem())));
        }
    }

    private Optional<Integer> typedDay() {
        try {
            return Optional.of(Integer.parseInt(day.getText().strip()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** The date as typed, even if the picker hasn't committed it: empty when unreadable, empty inside when blank. */
    private static Optional<Optional<LocalDate>> typedDate(DatePicker picker) {
        String text = picker.getEditor().getText();
        if (text == null || text.isBlank()) {
            return Optional.of(Optional.empty());
        }
        try {
            return Optional.of(Optional.ofNullable(picker.getConverter().fromString(text)));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private boolean fail(String message) {
        error.setText(message);
        return false;
    }

    private final class FrequencyConverter extends StringConverter<Frequency> {
        @Override
        public String toString(Frequency value) {
            return value == null ? "" : resources.getString(Messages.frequencyKey(value));
        }

        @Override
        public Frequency fromString(String text) {
            throw new UnsupportedOperationException("The frequency is chosen, not typed");
        }
    }

    private static final class CurrencyConverter extends StringConverter<Currency> {
        @Override
        public String toString(Currency value) {
            return value == null ? "" : value.code();
        }

        @Override
        public Currency fromString(String text) {
            return Currency.valueOf(text);
        }
    }

    private static final class CategoryCell extends ListCell<Category> {
        @Override
        protected void updateItem(Category item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.name());
        }
    }
}
