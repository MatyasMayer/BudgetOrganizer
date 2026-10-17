package org.example.budget.ui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.stage.Window;
import org.example.budget.domain.BudgetService;
import org.example.budget.domain.Category;
import org.example.budget.domain.Month;
import org.example.budget.domain.Transaction;
import org.example.budget.domain.TransactionFilter;
import org.example.budget.domain.TransactionType;

import java.text.MessageFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The Transaction list: filters by Month, type and Category, searches Notes, and opens the editor. */
public class TransactionsController {

    private final BudgetService service;
    private final Locale locale;
    private final DateTimeFormatter dateFormat;
    private final DateTimeFormatter monthFormat;

    private Map<Long, Category> categoriesById = Map.of();
    private Set<Long> generatedIds = Set.of();

    @FXML
    private ResourceBundle resources;
    @FXML
    private ComboBox<Optional<Month>> monthFilter;
    @FXML
    private ComboBox<Optional<TransactionType>> typeFilter;
    @FXML
    private ComboBox<Optional<Category>> categoryFilter;
    @FXML
    private TextField noteSearch;
    @FXML
    private TableView<Transaction> table;
    @FXML
    private TableColumn<Transaction, String> dateColumn;
    @FXML
    private TableColumn<Transaction, String> typeColumn;
    @FXML
    private TableColumn<Transaction, String> categoryColumn;
    @FXML
    private TableColumn<Transaction, String> amountColumn;
    @FXML
    private TableColumn<Transaction, String> originalColumn;
    @FXML
    private TableColumn<Transaction, String> noteColumn;
    @FXML
    private Label summary;
    @FXML
    private Button editButton;
    @FXML
    private Button deleteButton;

    public TransactionsController(BudgetService service, Locale locale) {
        this.service = service;
        this.locale = locale;
        this.dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
        this.monthFormat = DateTimeFormatter.ofLocalizedPattern("yMMMM").withLocale(locale);
    }

    @FXML
    private void initialize() {
        monthFilter.setCellFactory(list -> cell(month -> month.firstDay().format(monthFormat),
                "transactions.filter.all-months"));
        monthFilter.setButtonCell(monthFilter.getCellFactory().call(null));
        typeFilter.setCellFactory(list -> cell(type -> resources.getString(Messages.typeKey(type)),
                "transactions.filter.all-types"));
        typeFilter.setButtonCell(typeFilter.getCellFactory().call(null));
        categoryFilter.setCellFactory(list -> cell(Category::name, "transactions.filter.all-categories"));
        categoryFilter.setButtonCell(categoryFilter.getCellFactory().call(null));

        typeFilter.getItems().setAll(List.of(Optional.empty(),
                Optional.of(TransactionType.EXPENSE), Optional.of(TransactionType.INCOME)));
        typeFilter.setValue(Optional.empty());
        monthFilter.setValue(Optional.of(service.currentMonth()));
        refreshFilterOptions();
        categoryFilter.setValue(Optional.empty());

        dateColumn.setCellValueFactory(row -> text(row.getValue().date().format(dateFormat)));
        typeColumn.setCellValueFactory(row -> text(typeLabel(row.getValue())));
        categoryColumn.setCellValueFactory(row -> text(categoryName(row.getValue().categoryId())));
        amountColumn.setCellValueFactory(row -> text(row.getValue().amount().format(locale)));
        originalColumn.setCellValueFactory(row -> text(originalAmount(row.getValue())));
        noteColumn.setCellValueFactory(row -> text(row.getValue().note()));
        table.setRowFactory(view -> {
            TableRow<Transaction> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    edit(row.getItem());
                }
            });
            return row;
        });
        editButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        deleteButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        monthFilter.valueProperty().addListener((obs, previous, value) -> refreshList());
        typeFilter.valueProperty().addListener((obs, previous, value) -> {
            refreshFilterOptions();
            refreshList();
        });
        categoryFilter.valueProperty().addListener((obs, previous, value) -> refreshList());
        noteSearch.textProperty().addListener((obs, previous, value) -> refreshList());
        refreshList();
    }

    @FXML
    private void newTransaction() {
        if (TransactionEditorController.open(window(), service, resources, locale, Optional.empty())) {
            refreshAll();
        }
    }

    @FXML
    private void editSelected() {
        Optional.ofNullable(table.getSelectionModel().getSelectedItem()).ifPresent(this::edit);
    }

    @FXML
    private void deleteSelected() {
        Transaction selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, describe(selected), ButtonType.OK, ButtonType.CANCEL);
        confirm.initOwner(window());
        confirm.setTitle(resources.getString("transactions.delete.confirm.title"));
        confirm.setHeaderText(resources.getString("transactions.delete.confirm.header"));
        if (confirm.showAndWait().filter(ButtonType.OK::equals).isPresent()) {
            service.deleteTransaction(selected.id());
            refreshAll();
        }
    }

    private void edit(Transaction transaction) {
        if (TransactionEditorController.open(window(), service, resources, locale, Optional.of(transaction))) {
            refreshAll();
        }
    }

    private void refreshAll() {
        refreshFilterOptions();
        refreshList();
    }

    /** Months that have Transactions (plus the current one) and the Categories of the chosen type. */
    private void refreshFilterOptions() {
        Optional<Month> month = monthFilter.getValue();
        TreeSet<Month> months = service.transactions(TransactionFilter.ALL).stream()
                .map(transaction -> Month.of(transaction.date()))
                .collect(Collectors.toCollection(() -> new TreeSet<Month>(Comparator.reverseOrder())));
        months.add(service.currentMonth());
        month.ifPresent(months::add);
        monthFilter.getItems().setAll(Stream.concat(Stream.of(Optional.<Month>empty()),
                months.stream().map(Optional::of)).toList());
        monthFilter.setValue(month);

        List<Category> all = Stream.of(TransactionType.values())
                .flatMap(type -> service.categories(type).stream())
                .toList();
        categoriesById = all.stream().collect(Collectors.toMap(Category::id, Function.identity()));
        Optional<TransactionType> type = selected(typeFilter);
        Optional<Category> category = selected(categoryFilter);
        List<Optional<Category>> offered = Stream.concat(Stream.of(Optional.<Category>empty()), all.stream()
                .filter(c -> type.map(t -> t == c.type()).orElse(true))
                .map(Optional::of)).toList();
        categoryFilter.getItems().setAll(offered);
        categoryFilter.setValue(category.filter(c -> offered.contains(Optional.of(c))).isPresent()
                ? category : Optional.empty());
    }

    private void refreshList() {
        TransactionFilter filter = new TransactionFilter(
                selected(monthFilter),
                selected(categoryFilter).map(Category::id),
                selected(typeFilter),
                noteSearch.getText());
        List<Transaction> found = service.transactions(filter);
        generatedIds = service.generatedTransactionIds();
        table.getItems().setAll(found);
        summary.setText(MessageFormat.format(resources.getString("transactions.count"), found.size()));
    }

    private static <T> Optional<T> selected(ComboBox<Optional<T>> box) {
        return Optional.ofNullable(box.getValue()).flatMap(Function.identity());
    }

    /** E.g. "Expense", "Refund", or "Expense (recurring)" for one generated from a Recurring Template. */
    private String typeLabel(Transaction transaction) {
        String type = transaction.isRefund()
                ? resources.getString("transactions.refund")
                : resources.getString(Messages.typeKey(transaction.type()));
        return generatedIds.contains(transaction.id())
                ? MessageFormat.format(resources.getString("transactions.generated"), type)
                : type;
    }

    /**
     * Marks a EUR Transaction with its Original Amount, e.g. "EUR 19.99", and whether a Charged Amount was used
     * or it still uses a Provisional Rate (a Charged Amount is final, so it isn't marked provisional).
     */
    private String originalAmount(Transaction transaction) {
        return transaction.details().conversion()
                .map(c -> c.chargedAmount().isPresent()
                        ? MessageFormat.format(resources.getString("transactions.original.charged"),
                                c.originalAmount().format(locale))
                        : c.provisionalRate()
                        ? MessageFormat.format(resources.getString("transactions.original.provisional"),
                                c.originalAmount().format(locale))
                        : c.originalAmount().format(locale))
                .orElse("");
    }

    private String categoryName(long id) {
        Category category = categoriesById.get(id);
        return category == null ? "" : category.name();
    }

    private String describe(Transaction transaction) {
        return String.join(" · ", Stream.of(transaction.date().format(dateFormat),
                categoryName(transaction.categoryId()), transaction.amount().format(locale), transaction.note())
                .filter(part -> !part.isEmpty()).toList());
    }

    private Window window() {
        return table.getScene().getWindow();
    }

    private static ReadOnlyStringWrapper text(String value) {
        return new ReadOnlyStringWrapper(value);
    }

    private <T> ListCell<Optional<T>> cell(Function<T, String> name, String allKey) {
        return new ListCell<>() {
            @Override
            protected void updateItem(Optional<T> item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? resources.getString(allKey)
                        : item.map(name).orElseGet(() -> resources.getString(allKey)));
            }
        };
    }
}
