package org.example.budget.ui;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.example.budget.domain.AvailableRate;
import org.example.budget.domain.BudgetService;
import org.example.budget.domain.Category;
import org.example.budget.domain.CnbRate;
import org.example.budget.domain.Conversion;
import org.example.budget.domain.Currency;
import org.example.budget.domain.InvalidTransactionException;
import org.example.budget.domain.Money;
import org.example.budget.domain.OriginalAmount;
import org.example.budget.domain.Transaction;
import org.example.budget.domain.TransactionDetails;
import org.example.budget.domain.TransactionType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.text.NumberFormat;
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
 * An Expense can be marked Savings-Funded; if saving it leaves Savings below zero, a warning follows (it's saved).
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
    private ComboBox<Currency> currency;
    @FXML
    private GridPane foreignCurrency;
    @FXML
    private Label cnbRate;
    @FXML
    private Label manualRateLabel;
    @FXML
    private TextField manualRate;
    @FXML
    private Label czkAmount;
    @FXML
    private TextField chargedAmount;
    @FXML
    private ComboBox<Category> category;
    @FXML
    private TextField note;
    @FXML
    private CheckBox savingsFunded;
    @FXML
    private Label error;

    /**
     * The rate looked up for {@link #rateDate}: the ČNB Rate, or offline a cached one as a Provisional Rate;
     * empty while loading or when there is no rate at all (the user then enters one or a Charged Amount).
     */
    private Optional<AvailableRate> rate = Optional.empty();
    private LocalDate rateDate;
    private boolean rateLoading;
    /** Discards the answer of a lookup that a newer one (another date or currency) has replaced. */
    private long lookup;

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
        loader.setController(controller);
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
        savingsFunded.managedProperty().bind(savingsFunded.visibleProperty());
        savingsFunded.visibleProperty().bind(expenseType.selectedProperty());
        currency.getItems().setAll(Currency.values());
        currency.setConverter(new CurrencyConverter());
        foreignCurrency.managedProperty().bind(foreignCurrency.visibleProperty());
        manualRate.managedProperty().bind(manualRate.visibleProperty());
        manualRateLabel.visibleProperty().bind(manualRate.visibleProperty());
        manualRateLabel.managedProperty().bind(manualRate.visibleProperty());
        manualRate.setVisible(false);

        existing.ifPresentOrElse(this::fill, () -> {
            date.setValue(service.today());
            expenseType.setSelected(true);
            currency.setValue(Currency.CZK);
        });

        currency.valueProperty().addListener((obs, previous, value) -> refreshRate());
        date.valueProperty().addListener((obs, previous, value) -> refreshRate());
        amount.textProperty().addListener((obs, previous, value) -> showConversion());
        chargedAmount.textProperty().addListener((obs, previous, value) -> showConversion());
        manualRate.textProperty().addListener((obs, previous, value) -> showConversion());
        refreshRate();
    }

    private void fill(Transaction transaction) {
        (transaction.type() == TransactionType.INCOME ? incomeType : expenseType).setSelected(true);
        date.setValue(transaction.date());
        transaction.details().conversion().ifPresentOrElse(conversion -> {
            currency.setValue(conversion.originalAmount().currency());
            amount.setText(conversion.originalAmount().formatAmount(locale));
            chargedAmount.setText(conversion.chargedAmount().map(m -> m.formatAmount(locale)).orElse(""));
            // Keep the rate it was saved with while the date stays the same.
            rate = Optional.of(new AvailableRate(conversion.cnbRate(), conversion.provisionalRate()));
            rateDate = transaction.date();
        }, () -> {
            currency.setValue(Currency.CZK);
            amount.setText(transaction.amount().formatAmount(locale));
        });
        category.getItems().stream()
                .filter(c -> c.id() == transaction.categoryId())
                .findFirst()
                .ifPresent(category.getSelectionModel()::select);
        note.setText(transaction.note());
        savingsFunded.setSelected(transaction.details().savingsFunded());
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
        TransactionDetails details;
        if (isForeign()) {
            Optional<Optional<Money>> charged = typedChargedAmount();
            if (charged.isEmpty()) {
                return fail(MessageFormat.format(resources.getString("editor.error.charged-amount"),
                        Money.ofHalere(49_310).formatAmount(locale)));
            }
            OriginalAmount original = new OriginalAmount(currency.getValue(), chosenAmount.get().halere());
            if (rateLoading) {
                return fail(resources.getString("editor.cnb-rate.loading"));
            }
            Optional<Conversion> conversion = rateFor(chosenDate.get())
                    .map(r -> r.convert(original, charged.get()));
            if (conversion.isEmpty() && !isBlank(manualRate.getText())) {
                Optional<BigDecimal> entered = typedManualRate();
                if (entered.isEmpty()) {
                    return fail(MessageFormat.format(resources.getString("editor.error.manual-rate"),
                            formatRate(new BigDecimal("24.325"))));
                }
                conversion = Optional.of(
                        service.convertAtRate(original, chosenDate.get(), entered.get(), charged.get()));
            }
            if (conversion.isEmpty()) {
                conversion = service.convert(original, chosenDate.get(), charged.get());
            }
            if (conversion.isEmpty()) {
                return fail(resources.getString("editor.error.rate-unavailable"));
            }
            details = TransactionDetails.converted(
                    chosenDate.get(), type(), category.getValue().id(), conversion.get(), note.getText());
        } else {
            details = new TransactionDetails(
                    chosenDate.get(), type(), category.getValue().id(), chosenAmount.get(), note.getText());
        }
        TransactionDetails saved = details.withSavingsFunded(type() == TransactionType.EXPENSE
                && savingsFunded.isSelected());
        try {
            existing.ifPresentOrElse(
                    transaction -> service.editTransaction(transaction.id(), saved),
                    () -> service.recordTransaction(saved));
            service.negativeSavingsWarning(saved).ifPresent(this::warnAboutNegativeSavings);
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

    /** The Savings-Funded Expense is already saved; this only informs. */
    private void warnAboutNegativeSavings(Money savings) {
        Alert warning = new Alert(Alert.AlertType.WARNING);
        warning.initOwner(error.getScene().getWindow());
        warning.setTitle(resources.getString("editor.savings-warning.title"));
        warning.setHeaderText(resources.getString("editor.savings-warning.header"));
        warning.setContentText(MessageFormat.format(resources.getString("editor.savings-warning"),
                savings.format(locale)));
        warning.showAndWait();
    }

    private boolean fail(String message) {
        error.setText(message);
        return false;
    }

    // --- Foreign currency ---

    private boolean isForeign() {
        return currency.getValue() != null && !currency.getValue().isBaseCurrency();
    }

    /** The Charged Amount as typed: empty when unreadable, an empty Optional inside when left blank. */
    private Optional<Optional<Money>> typedChargedAmount() {
        String text = chargedAmount.getText();
        if (text == null || text.isBlank()) {
            return Optional.of(Optional.empty());
        }
        return Money.parse(text, locale).map(Optional::of);
    }

    private Optional<AvailableRate> rateFor(LocalDate day) {
        return day.equals(rateDate)
                ? rate.filter(r -> r.rate().currency() == currency.getValue())
                : Optional.empty();
    }

    /** The rate the user typed (CZK per unit) because none is available; empty when blank or unreadable. */
    private Optional<BigDecimal> typedManualRate() {
        String text = manualRate.getText();
        if (isBlank(text)) {
            return Optional.empty();
        }
        try {
            BigDecimal value = new BigDecimal(text.strip().replace(',', '.'));
            return value.signum() > 0 ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /**
     * Looks up the rate for the chosen date off the UI thread, since it may need the network. When ČNB can't be
     * reached the most recent cached rate comes back as a Provisional Rate.
     */
    private void refreshRate() {
        foreignCurrency.setVisible(isForeign());
        LocalDate day = date.getValue();
        if (!isForeign() || day == null || rateFor(day).isPresent()) {
            showConversion();
            return;
        }
        Currency chosen = currency.getValue();
        long thisLookup = ++lookup;
        rateLoading = true;
        rate = Optional.empty();
        rateDate = day;
        showConversion();
        Thread.ofVirtual().start(() -> {
            Optional<AvailableRate> found = service.rateToUse(chosen, day);
            Platform.runLater(() -> {
                if (thisLookup == lookup) {
                    rateLoading = false;
                    rate = found;
                    showConversion();
                }
            });
        });
    }

    private void showConversion() {
        if (!isForeign()) {
            return;
        }
        LocalDate day = date.getValue();
        Optional<AvailableRate> available = day == null ? Optional.empty() : rateFor(day);
        manualRate.setVisible(!rateLoading && available.isEmpty());
        if (rateLoading) {
            cnbRate.setText(resources.getString("editor.cnb-rate.loading"));
        } else if (available.isEmpty()) {
            cnbRate.setText(resources.getString("editor.cnb-rate.none"));
        } else {
            CnbRate r = available.get().rate();
            String key = available.get().provisional() ? "editor.cnb-rate.provisional" : "editor.cnb-rate.value";
            cnbRate.setText(MessageFormat.format(resources.getString(key),
                    r.currency().code(), formatRate(r.czkPerUnit()), r.validFor().format(dateFormat())));
        }

        Optional<OriginalAmount> original = Money.parse(amount.getText(), locale)
                .map(m -> new OriginalAmount(currency.getValue(), m.halere()));
        Optional<CnbRate> current = available.map(AvailableRate::rate).or(() -> day == null
                ? Optional.empty()
                : typedManualRate().map(r -> new CnbRate(currency.getValue(), day, r)));
        Optional<Money> converted = original.flatMap(o -> current.map(r -> r.convert(o)));
        Optional<Money> charged = typedChargedAmount().flatMap(c -> c);
        if (charged.isPresent()) {
            czkAmount.setText(MessageFormat.format(resources.getString("editor.czk-amount.charged"),
                    charged.get().format(locale), converted.map(m -> m.format(locale)).orElse("–")));
        } else {
            czkAmount.setText(converted.map(m -> m.format(locale)).orElse("–"));
        }
    }

    private String formatRate(BigDecimal czkPerUnit) {
        NumberFormat number = NumberFormat.getNumberInstance(locale);
        number.setMinimumFractionDigits(3);
        number.setMaximumFractionDigits(6);
        return number.format(czkPerUnit);
    }

    private DateTimeFormatter dateFormat() {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
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

    /** Shows and reads dates in the UI language, e.g. "17. 10. 2026" in Czech. */
    static final class LocalizedDateConverter extends StringConverter<LocalDate> {
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
