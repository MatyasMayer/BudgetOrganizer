package org.example.budget.ui;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.example.budget.domain.BudgetService;
import org.example.budget.domain.Category;
import org.example.budget.domain.InvalidBudgetLimitException;
import org.example.budget.domain.InvalidCategoryException;
import org.example.budget.domain.Money;
import org.example.budget.domain.Month;
import org.example.budget.domain.TransactionType;

import java.text.MessageFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * The Categories screen: Income and Expense Categories in separate lists. Adds, renames and deletes them;
 * deleting a Category with Transactions asks for a replacement of the same type to merge into. For the
 * selected Expense Category it sets and clears the Default Limit and the Limit Override of a chosen Month.
 */
public class CategoriesController {

    private final BudgetService service;
    private final Locale locale;
    private final DateTimeFormatter monthFormat;

    /** The Month whose Limit Override is shown and edited. */
    private Month limitMonth;

    @FXML
    private ResourceBundle resources;
    @FXML
    private ListView<Category> expenseList;
    @FXML
    private ListView<Category> incomeList;
    @FXML
    private Button renameExpense;
    @FXML
    private Button deleteExpense;
    @FXML
    private Button renameIncome;
    @FXML
    private Button deleteIncome;
    @FXML
    private Label limitMonthTitle;
    @FXML
    private Label limitsHint;
    @FXML
    private GridPane limitsGrid;
    @FXML
    private Label defaultLimit;
    @FXML
    private Button clearDefault;
    @FXML
    private Label overrideTitle;
    @FXML
    private Label limitOverride;
    @FXML
    private Button clearOverride;
    @FXML
    private Label effectiveLimit;

    public CategoriesController(BudgetService service, Locale locale) {
        this.service = service;
        this.locale = locale;
        this.monthFormat = DateTimeFormatter.ofLocalizedPattern("yMMMM").withLocale(locale);
    }

    @FXML
    private void initialize() {
        setUp(expenseList, renameExpense, deleteExpense);
        setUp(incomeList, renameIncome, deleteIncome);
        limitMonth = service.currentMonth();
        limitsGrid.managedProperty().bind(limitsGrid.visibleProperty());
        limitsHint.managedProperty().bind(limitsHint.visibleProperty());
        expenseList.getSelectionModel().selectedItemProperty().addListener((obs, previous, now) -> showLimits());
        refresh();
    }

    private void setUp(ListView<Category> list, Button rename, Button delete) {
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : label(item));
            }
        });
        rename.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        // From Savings can't be deleted, so its Delete button stays off.
        delete.disableProperty().bind(Bindings.createBooleanBinding(
                () -> selected(list).map(Category::system).orElse(true),
                list.getSelectionModel().selectedItemProperty()));
    }

    @FXML
    private void addExpenseCategory() {
        add(TransactionType.EXPENSE);
    }

    @FXML
    private void addIncomeCategory() {
        add(TransactionType.INCOME);
    }

    @FXML
    private void renameExpenseCategory() {
        selected(expenseList).ifPresent(this::rename);
    }

    @FXML
    private void renameIncomeCategory() {
        selected(incomeList).ifPresent(this::rename);
    }

    @FXML
    private void deleteExpenseCategory() {
        selected(expenseList).ifPresent(this::delete);
    }

    @FXML
    private void deleteIncomeCategory() {
        selected(incomeList).ifPresent(this::delete);
    }

    @FXML
    private void previousLimitMonth() {
        limitMonth = limitMonth.previous();
        showLimits();
    }

    @FXML
    private void nextLimitMonth() {
        limitMonth = limitMonth.next();
        showLimits();
    }

    @FXML
    private void setDefaultLimit() {
        selected(expenseList).ifPresent(category -> askForLimit(
                format("categories.limits.default.title", category.name()), service.defaultLimit(category.id()))
                .ifPresent(amount -> attemptLimit(() -> service.setDefaultLimit(category.id(), amount))));
    }

    @FXML
    private void clearDefaultLimit() {
        selected(expenseList).ifPresent(category -> attemptLimit(() -> service.clearDefaultLimit(category.id())));
    }

    @FXML
    private void setLimitOverride() {
        selected(expenseList).ifPresent(category -> askForLimit(
                format("categories.limits.override.title", category.name(), monthName(limitMonth)),
                service.limitOverride(category.id(), limitMonth))
                .ifPresent(amount -> attemptLimit(
                        () -> service.setLimitOverride(category.id(), limitMonth, amount))));
    }

    @FXML
    private void clearLimitOverride() {
        selected(expenseList).ifPresent(
                category -> attemptLimit(() -> service.clearLimitOverride(category.id(), limitMonth)));
    }

    /** Shows the selected Expense Category's Budget Limits for the chosen Month. */
    private void showLimits() {
        limitMonthTitle.setText(monthName(limitMonth));
        overrideTitle.setText(format("categories.limits.override", monthName(limitMonth)));
        Optional<Category> category = selected(expenseList);
        limitsGrid.setVisible(category.isPresent());
        limitsHint.setVisible(category.isEmpty());
        category.ifPresent(chosen -> {
            Optional<Money> byDefault = service.defaultLimit(chosen.id());
            Optional<Money> override = service.limitOverride(chosen.id(), limitMonth);
            defaultLimit.setText(limitText(byDefault));
            limitOverride.setText(limitText(override));
            clearDefault.setDisable(byDefault.isEmpty());
            clearOverride.setDisable(override.isEmpty());
            effectiveLimit.setText(format("categories.limits.effective", monthName(limitMonth),
                    limitText(service.effectiveLimit(chosen.id(), limitMonth))));
        });
    }

    /** Asks for a limit amount until it's a valid amount or the dialog is cancelled. */
    private Optional<Money> askForLimit(String title, Optional<Money> current) {
        TextInputDialog dialog = new TextInputDialog(current.map(amount -> amount.formatAmount(locale)).orElse(""));
        dialog.initOwner(window());
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(resources.getString("categories.limits.amount"));
        while (true) {
            Optional<String> text = dialog.showAndWait();
            if (text.isEmpty()) {
                return Optional.empty();
            }
            Optional<Money> amount = Money.parse(text.get(), locale);
            if (amount.isPresent()) {
                return amount;
            }
            showError(format("categories.limits.error.amount", Money.ofHalere(500_000).formatAmount(locale)));
        }
    }

    /** Runs a Budget Limit change, shows the translated reason if it's refused, and shows the limits again. */
    private void attemptLimit(Runnable change) {
        try {
            change.run();
        } catch (InvalidBudgetLimitException e) {
            showError(resources.getString(Messages.budgetLimitProblemKey(e.problem())));
        }
        showLimits();
    }

    private String limitText(Optional<Money> limit) {
        return limit.map(amount -> amount.format(locale)).orElseGet(() -> resources.getString("categories.limits.none"));
    }

    private String monthName(Month month) {
        return month.firstDay().format(monthFormat);
    }

    private void add(TransactionType type) {
        askForName("categories.add.title", "").ifPresent(name -> attempt(() -> service.addCategory(name, type)));
    }

    private void rename(Category category) {
        askForName("categories.rename.title", category.name())
                .ifPresent(name -> attempt(() -> service.renameCategory(category.id(), name)));
    }

    private void delete(Category category) {
        if (!service.needsReplacement(category.id())) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    resources.getString("categories.delete.confirm.content"), ButtonType.OK, ButtonType.CANCEL);
            confirm.initOwner(window());
            confirm.setTitle(resources.getString("categories.delete.title"));
            confirm.setHeaderText(format("categories.delete.confirm.header", category.name()));
            if (confirm.showAndWait().filter(ButtonType.OK::equals).isPresent()) {
                attempt(() -> service.deleteCategory(category.id()));
            }
            return;
        }

        List<Category> replacements = service.categories(category.type()).stream()
                .filter(other -> other.id() != category.id())
                .toList();
        if (replacements.isEmpty()) {
            showError(resources.getString("categories.merge.no-replacement"));
            return;
        }
        ComboBox<Category> replacement = new ComboBox<>();
        replacement.getItems().setAll(replacements);
        replacement.getSelectionModel().selectFirst();
        replacement.setConverter(new NameConverter());
        replacement.setMaxWidth(Double.MAX_VALUE);
        HBox content = new HBox(8, new Label(resources.getString("categories.merge.content")), replacement);
        content.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(replacement, Priority.ALWAYS);

        Dialog<ButtonType> choose = new Dialog<>();
        choose.initOwner(window());
        choose.setTitle(resources.getString("categories.delete.title"));
        choose.setHeaderText(format("categories.merge.header", category.name()));
        choose.getDialogPane().setContent(content);
        choose.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        if (choose.showAndWait().filter(ButtonType.OK::equals).isPresent()) {
            attempt(() -> service.deleteCategory(category.id(), replacement.getValue().id()));
        }
    }

    private Optional<String> askForName(String titleKey, String current) {
        TextInputDialog dialog = new TextInputDialog(current);
        dialog.initOwner(window());
        dialog.setTitle(resources.getString(titleKey));
        dialog.setHeaderText(null);
        dialog.setContentText(resources.getString("categories.name"));
        return dialog.showAndWait();
    }

    /** Runs a Category change, shows the translated reason if it's refused, and refreshes the lists. */
    private void attempt(Runnable change) {
        try {
            change.run();
        } catch (InvalidCategoryException e) {
            showError(resources.getString(Messages.categoryProblemKey(e.problem())));
        }
        refresh();
    }

    private void refresh() {
        expenseList.getItems().setAll(service.categories(TransactionType.EXPENSE));
        incomeList.getItems().setAll(service.categories(TransactionType.INCOME));
        showLimits();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alert.initOwner(window());
        alert.setTitle(resources.getString("screen.categories"));
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private String label(Category category) {
        return category.system()
                ? category.name() + " (" + resources.getString("categories.system") + ")"
                : category.name();
    }

    private String format(String key, Object... arguments) {
        return MessageFormat.format(resources.getString(key), arguments);
    }

    private static Optional<Category> selected(ListView<Category> list) {
        return Optional.ofNullable(list.getSelectionModel().getSelectedItem());
    }

    private Window window() {
        return expenseList.getScene().getWindow();
    }

    private static final class NameConverter extends StringConverter<Category> {
        @Override
        public String toString(Category category) {
            return category == null ? "" : category.name();
        }

        @Override
        public Category fromString(String text) {
            throw new UnsupportedOperationException();
        }
    }
}
