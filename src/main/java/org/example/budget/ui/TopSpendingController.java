package org.example.budget.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.example.budget.domain.BudgetService;
import org.example.budget.domain.Category;
import org.example.budget.domain.CategoryRank;
import org.example.budget.domain.Month;
import org.example.budget.domain.TopSpending;
import org.example.budget.domain.Transaction;
import org.example.budget.domain.TransactionType;

import java.text.MessageFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Top spending screen: Expense Categories ranked by total and the five biggest single Expenses, for a chosen
 * Month or year. Savings-Funded Expenses are included, marked "from Savings".
 */
public class TopSpendingController {

    private final BudgetService service;
    private final Locale locale;
    private final DateTimeFormatter monthFormat;
    private final DateTimeFormatter dateFormat;

    private Month month;
    private int year;

    @FXML
    private ResourceBundle resources;
    @FXML
    private ToggleGroup scope;
    @FXML
    private ToggleButton monthScope;
    @FXML
    private ToggleButton yearScope;
    @FXML
    private Label periodTitle;
    @FXML
    private VBox sections;

    public TopSpendingController(BudgetService service, Locale locale) {
        this.service = service;
        this.locale = locale;
        this.monthFormat = DateTimeFormatter.ofLocalizedPattern("yMMMM").withLocale(locale);
        this.dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
    }

    @FXML
    private void initialize() {
        month = service.currentMonth();
        year = month.year();
        monthScope.setSelected(true);
        // A toggle group lets the selected button be clicked off; keep one scope always selected.
        scope.selectedToggleProperty().addListener((obs, previous, selected) -> {
            if (selected == null) {
                previous.setSelected(true);
            } else {
                refresh();
            }
        });
        refresh();
    }

    @FXML
    private void previous() {
        if (monthScope.isSelected()) {
            month = month.previous();
        } else {
            year--;
        }
        refresh();
    }

    @FXML
    private void next() {
        if (monthScope.isSelected()) {
            month = month.next();
        } else {
            year++;
        }
        refresh();
    }

    @FXML
    private void current() {
        month = service.currentMonth();
        year = month.year();
        refresh();
    }

    private void refresh() {
        boolean byMonth = monthScope.isSelected();
        periodTitle.setText(byMonth ? month.firstDay().format(monthFormat) : String.valueOf(year));
        TopSpending top = byMonth ? service.topSpending(month) : service.topSpending(year);
        Map<Long, Category> categoriesById = service.categories(TransactionType.EXPENSE).stream()
                .collect(Collectors.toMap(Category::id, Function.identity()));
        sections.getChildren().setAll(
                heading("top-spending.categories"), categories(top),
                heading("top-spending.biggest"), biggest(top, categoriesById));
    }

    private GridPane categories(TopSpending top) {
        GridPane table = table();
        if (top.categories().isEmpty()) {
            table.add(muted(resources.getString("top-spending.empty")), 0, 0);
            return table;
        }
        int row = 0;
        int position = 1;
        for (CategoryRank rank : top.categories()) {
            Label name = new Label(rank.category().name());
            Label total = amount(rank.total().format(locale));
            table.addRow(row++, muted(position++ + "."), name, total);
            if (rank.hasSavingsFunded()) {
                Label savingsFunded = muted(MessageFormat.format(resources.getString("dashboard.from-savings"),
                        rank.savingsFunded().format(locale)));
                savingsFunded.setTooltip(new Tooltip(resources.getString("dashboard.from-savings.hint")));
                table.add(savingsFunded, 1, row++, 2, 1);
            }
        }
        return table;
    }

    private GridPane biggest(TopSpending top, Map<Long, Category> categoriesById) {
        GridPane table = table();
        if (top.biggestExpenses().isEmpty()) {
            table.add(muted(resources.getString("top-spending.empty")), 0, 0);
            return table;
        }
        int row = 0;
        for (Transaction expense : top.biggestExpenses()) {
            Category category = categoriesById.get(expense.categoryId());
            String note = expense.details().hasNote() ? expense.note() : "";
            if (expense.details().savingsFunded()) {
                note = MessageFormat.format(resources.getString("transactions.from-savings"), note).strip();
            }
            table.addRow(row++, new Label(expense.date().format(dateFormat)),
                    new Label(category == null ? "" : category.name()), amount(expense.amount().format(locale)),
                    new Label(note));
        }
        return table;
    }

    private static GridPane table() {
        GridPane table = new GridPane();
        table.setHgap(24);
        table.setVgap(6);
        return table;
    }

    private Label heading(String key) {
        Label heading = new Label(resources.getString(key));
        heading.getStyleClass().add("title-4");
        return heading;
    }

    private static Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("text-muted");
        return label;
    }

    private static Label amount(String text) {
        Label label = new Label(text);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setStyle("-fx-alignment: CENTER-RIGHT;");
        GridPane.setHgrow(label, Priority.ALWAYS);
        return label;
    }
}
