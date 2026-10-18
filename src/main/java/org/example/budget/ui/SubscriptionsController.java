package org.example.budget.ui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.example.budget.domain.BudgetService;
import org.example.budget.domain.Category;
import org.example.budget.domain.RecurringTemplateDetails;
import org.example.budget.domain.Subscription;
import org.example.budget.domain.SubscriptionOverview;
import org.example.budget.domain.TransactionType;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Subscriptions screen: every active Expense Recurring Template with its monthly and yearly cost in CZK,
 * and the grand totals.
 */
public class SubscriptionsController {

    private final BudgetService service;
    private final Locale locale;

    private Map<Long, Category> categoriesById = Map.of();

    @FXML
    private ResourceBundle resources;
    @FXML
    private Label headline;
    @FXML
    private TableView<Subscription> table;
    @FXML
    private TableColumn<Subscription, String> nameColumn;
    @FXML
    private TableColumn<Subscription, String> categoryColumn;
    @FXML
    private TableColumn<Subscription, String> monthlyColumn;
    @FXML
    private TableColumn<Subscription, String> yearlyColumn;
    @FXML
    private Label totals;
    @FXML
    private Label unpriced;

    public SubscriptionsController(BudgetService service, Locale locale) {
        this.service = service;
        this.locale = locale;
    }

    @FXML
    private void initialize() {
        nameColumn.setCellValueFactory(row -> text(describe(row.getValue().template().details())));
        categoryColumn.setCellValueFactory(row -> text(categoryName(row.getValue().template().details().categoryId())));
        monthlyColumn.setCellValueFactory(row -> text(row.getValue().monthlyCost().format(locale)));
        yearlyColumn.setCellValueFactory(row -> text(row.getValue().yearlyCost().format(locale)));
        refresh();
    }

    private void refresh() {
        categoriesById = service.categories(TransactionType.EXPENSE).stream()
                .collect(Collectors.toMap(Category::id, Function.identity()));
        SubscriptionOverview overview = service.subscriptionOverview();
        table.getItems().setAll(overview.subscriptions());
        headline.setText(MessageFormat.format(resources.getString("subscriptions.headline"),
                overview.yearlyTotal().format(locale)));
        totals.setText(MessageFormat.format(resources.getString("subscriptions.totals"),
                overview.monthlyTotal().format(locale), overview.yearlyTotal().format(locale)));
        boolean anyUnpriced = !overview.unpriced().isEmpty();
        unpriced.setVisible(anyUnpriced);
        unpriced.setManaged(anyUnpriced);
        unpriced.setText(MessageFormat.format(resources.getString("subscriptions.unpriced"),
                overview.unpriced().stream().map(t -> describe(t.details())).collect(Collectors.joining(", "))));
    }

    private String describe(RecurringTemplateDetails details) {
        return details.hasNote() ? details.note() : categoryName(details.categoryId());
    }

    private String categoryName(long id) {
        Category category = categoriesById.get(id);
        return category == null ? "" : category.name();
    }

    private static ReadOnlyStringWrapper text(String value) {
        return new ReadOnlyStringWrapper(value);
    }
}
