package org.example.budget.ui;

import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;
import org.example.budget.domain.BudgetService;
import org.example.budget.domain.Money;
import org.example.budget.domain.Month;
import org.example.budget.domain.MonthSummary;
import org.example.budget.domain.MonthSummary.CategorySpending;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * The Month dashboard: total Income, Expenses and Leftover of the selected Month, and its Expenses by
 * Category as a pie chart plus a table. The view is rebuilt each time the screen is shown, so changes
 * made on other screens always appear; changes made from here refresh it straight away.
 */
public class DashboardController {

    /** Categorical slots before the rest is folded into one "Other Categories" slice. */
    private static final int NAMED_SLICES = 7;

    private final BudgetService service;
    private final Locale locale;
    private final DateTimeFormatter monthFormat;

    private Month month;

    @FXML
    private ResourceBundle resources;
    @FXML
    private Label monthTitle;
    @FXML
    private DatePicker monthPicker;
    @FXML
    private Label incomeTotal;
    @FXML
    private Label expensesTotal;
    @FXML
    private Label leftoverTotal;
    @FXML
    private PieChart pie;
    @FXML
    private Label noExpenses;
    @FXML
    private GridPane categoryTable;

    public DashboardController(BudgetService service, Locale locale) {
        this.service = service;
        this.locale = locale;
        this.monthFormat = DateTimeFormatter.ofLocalizedPattern("yMMMM").withLocale(locale);
    }

    @FXML
    private void initialize() {
        month = service.currentMonth();
        monthPicker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : date.format(monthFormat);
            }

            @Override
            public LocalDate fromString(String text) {
                return monthPicker.getValue();
            }
        });
        monthPicker.setEditable(false);
        monthPicker.valueProperty().addListener((obs, previous, date) -> {
            if (date != null && !month.contains(date)) {
                show(Month.of(date));
            }
        });
        refresh();
    }

    @FXML
    private void previousMonth() {
        show(month.previous());
    }

    @FXML
    private void nextMonth() {
        show(month.next());
    }

    @FXML
    private void currentMonth() {
        show(service.currentMonth());
    }

    @FXML
    private void newTransaction() {
        if (TransactionEditorController.open(pie.getScene().getWindow(), service, resources, locale,
                Optional.empty())) {
            refresh();
        }
    }

    private void show(Month selected) {
        month = selected;
        refresh();
    }

    private void refresh() {
        MonthSummary summary = service.monthSummary(month);
        monthTitle.setText(month.firstDay().format(monthFormat));
        if (monthPicker.getValue() == null || !month.contains(monthPicker.getValue())) {
            monthPicker.setValue(month.firstDay());
        }

        incomeTotal.setText(summary.income().format(locale));
        expensesTotal.setText(summary.expenses().format(locale));
        leftoverTotal.setText(summary.leftover().format(locale));
        leftoverTotal.getStyleClass().remove("danger");
        if (summary.leftover().halere() < 0) {
            leftoverTotal.getStyleClass().add("danger");
        }

        List<Slice> slices = slices(summary.expensesByCategory());
        Money pieTotal = slices.stream().map(Slice::total).reduce(Money.ZERO, Money::plus);
        pie.getData().clear();
        for (Slice slice : slices) {
            PieChart.Data data = new PieChart.Data(slice.name(), slice.total().halere());
            pie.getData().add(data);
            Tooltip.install(data.getNode(), new Tooltip(MessageFormat.format(
                    resources.getString("dashboard.slice"), slice.name(), slice.total().format(locale),
                    percent(slice.total(), pieTotal))));
        }
        boolean empty = slices.isEmpty();
        pie.setVisible(!empty);
        noExpenses.setVisible(empty);
        fillTable(summary.expensesByCategory());
    }

    /** Positive Category totals, largest first; past the named slots they fold into one slice. */
    private List<Slice> slices(List<CategorySpending> spending) {
        List<CategorySpending> positive = spending.stream().filter(s -> s.total().halere() > 0).toList();
        List<Slice> slices = new ArrayList<>();
        boolean fold = positive.size() > NAMED_SLICES + 1;
        int named = fold ? NAMED_SLICES : positive.size();
        for (int i = 0; i < named; i++) {
            slices.add(new Slice(positive.get(i).category().name(), positive.get(i).total()));
        }
        if (fold) {
            Money rest = positive.subList(named, positive.size()).stream()
                    .map(CategorySpending::total).reduce(Money.ZERO, Money::plus);
            slices.add(new Slice(resources.getString("dashboard.other-categories"), rest));
        }
        return slices;
    }

    /** Every Category with Expenses this Month, including ones a Refund brought to zero or below. */
    private void fillTable(List<CategorySpending> spending) {
        categoryTable.getChildren().clear();
        int slot = 0;
        for (int row = 0; row < spending.size(); row++) {
            CategorySpending item = spending.get(row);
            Region swatch = new Region();
            if (item.total().halere() > 0) {
                swatch.getStyleClass().addAll("category-swatch", "pie-legend-symbol",
                        "default-color" + Math.min(slot++, NAMED_SLICES));
            }
            Label name = new Label(item.category().name());
            Label amount = new Label(item.total().format(locale));
            amount.setMaxWidth(Double.MAX_VALUE);
            amount.setStyle("-fx-alignment: CENTER-RIGHT;");
            GridPane.setHgrow(amount, Priority.ALWAYS);
            categoryTable.addRow(row, swatch, name, amount);
        }
    }

    private String percent(Money part, Money whole) {
        NumberFormat format = NumberFormat.getPercentInstance(locale);
        if (whole.halere() == 0) {
            return format.format(0);
        }
        return format.format(BigDecimal.valueOf(part.halere())
                .divide(BigDecimal.valueOf(whole.halere()), 2, RoundingMode.HALF_UP));
    }

    private record Slice(String name, Money total) {
    }
}
