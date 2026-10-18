package org.example.budget.ui;

import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.example.budget.domain.BudgetService;
import org.example.budget.domain.CategoryPace;
import org.example.budget.domain.Deficit;
import org.example.budget.domain.Money;
import org.example.budget.domain.Month;
import org.example.budget.domain.MonthPace;
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
 * Category as a pie chart plus a table, where each Category with a Budget Limit gets a coloured progress bar.
 * Savings-Funded Expenses are part of their Category's slice and row, marked "from Savings". The Savings total
 * is shown next to the Month's totals. The view is rebuilt each time the screen is shown, so changes
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
    private Label savingsTotal;
    @FXML
    private Label deficitBadge;
    @FXML
    private VBox paceBox;
    @FXML
    private FlowPane paceFigures;
    @FXML
    private VBox paceCategories;
    @FXML
    private HBox uncoveredDeficits;
    @FXML
    private FlowPane deficitLinks;
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
        deficitBadge.managedProperty().bind(deficitBadge.visibleProperty());
        uncoveredDeficits.managedProperty().bind(uncoveredDeficits.visibleProperty());
        paceBox.managedProperty().bind(paceBox.visibleProperty());
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
        if (summary.deficit()) {
            leftoverTotal.getStyleClass().add("danger");
        }
        deficitBadge.setVisible(summary.deficit());
        Money savings = service.savings();
        savingsTotal.setText(savings.format(locale));
        savingsTotal.getStyleClass().remove("danger");
        if (savings.halere() < 0) {
            savingsTotal.getStyleClass().add("danger");
        }
        fillDeficits(service.uncoveredDeficits());
        fillPace();

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

    /** Pace, daily average and safe-to-spend, only for the running Month. */
    private void fillPace() {
        boolean running = month.equals(service.currentMonth());
        paceBox.setVisible(running);
        paceFigures.getChildren().clear();
        paceCategories.getChildren().clear();
        if (!running) {
            return;
        }
        MonthPace pace = service.monthPace();
        paceFigures.getChildren().addAll(
                figure("dashboard.pace", pace.pace()),
                figure("dashboard.daily-average", pace.dailyAverage()),
                figure("dashboard.safe-to-spend", pace.safeToSpendPerDay()));
        for (CategoryPace category : pace.categories()) {
            Label line = new Label(MessageFormat.format(resources.getString("dashboard.pace.category"),
                    category.category().name(), category.pace().format(locale), category.limit().format(locale),
                    category.safeToSpendPerDay().format(locale)));
            if (category.exceedsLimit()) {
                line.setText(MessageFormat.format(resources.getString("dashboard.pace.warning"), line.getText()));
                line.getStyleClass().add("danger");
            }
            paceCategories.getChildren().add(line);
        }
    }

    private Label figure(String labelKey, Money amount) {
        Label figure = new Label(MessageFormat.format(resources.getString(labelKey), amount.format(locale)));
        figure.setTooltip(new Tooltip(resources.getString(labelKey + ".hint")));
        return figure;
    }

    /** Every Month with an Uncovered Deficit as a link that opens it; hidden when there are none. */
    private void fillDeficits(List<Deficit> deficits) {
        deficitLinks.getChildren().clear();
        for (Deficit deficit : deficits) {
            Hyperlink link = new Hyperlink(MessageFormat.format(resources.getString("dashboard.uncovered-deficit"),
                    deficit.month().firstDay().format(monthFormat), deficit.leftover().format(locale)));
            link.getStyleClass().add("danger");
            link.setOnAction(event -> show(deficit.month()));
            deficitLinks.getChildren().add(link);
        }
        uncoveredDeficits.setVisible(!deficits.isEmpty());
    }

    /**
     * Positive Category totals (Savings-Funded Expenses included), largest first; past the named slots they fold
     * into one slice. A slice with Savings-Funded Expenses carries the "from Savings" marker.
     */
    private List<Slice> slices(List<CategorySpending> spending) {
        List<CategorySpending> positive = spending.stream().filter(s -> s.spent().halere() > 0).toList();
        List<Slice> slices = new ArrayList<>();
        boolean fold = positive.size() > NAMED_SLICES + 1;
        int named = fold ? NAMED_SLICES : positive.size();
        for (int i = 0; i < named; i++) {
            CategorySpending item = positive.get(i);
            String name = item.hasFromSavings()
                    ? MessageFormat.format(resources.getString("dashboard.from-savings.marker"), item.category().name())
                    : item.category().name();
            slices.add(new Slice(name, item.spent()));
        }
        if (fold) {
            Money rest = positive.subList(named, positive.size()).stream()
                    .map(CategorySpending::spent).reduce(Money.ZERO, Money::plus);
            slices.add(new Slice(resources.getString("dashboard.other-categories"), rest));
        }
        return slices;
    }

    /**
     * Every Category with Expenses this Month, including ones a Refund brought to zero or below, and every
     * limited Category; the limited ones show spent vs. effective limit with a bar below.
     */
    private void fillTable(List<CategorySpending> spending) {
        categoryTable.getChildren().clear();
        int slot = 0;
        int row = 0;
        for (CategorySpending item : spending) {
            Region swatch = new Region();
            if (item.spent().halere() > 0) {
                swatch.getStyleClass().addAll("category-swatch", "pie-legend-symbol",
                        "default-color" + Math.min(slot++, NAMED_SLICES));
            }
            Label name = new Label(item.category().name());
            Label amount = new Label(item.limit()
                    .map(limit -> MessageFormat.format(resources.getString("dashboard.limit"),
                            item.total().format(locale), limit.format(locale)))
                    .orElse(item.total().format(locale)));
            amount.setMaxWidth(Double.MAX_VALUE);
            amount.setStyle("-fx-alignment: CENTER-RIGHT;");
            GridPane.setHgrow(amount, Priority.ALWAYS);
            categoryTable.addRow(row++, swatch, name, amount);
            if (item.limit().isPresent()) {
                categoryTable.add(budgetBar(item, item.limit().get()), 1, row++, 2, 1);
            }
            if (item.hasFromSavings()) {
                Label fromSavings = new Label(MessageFormat.format(resources.getString("dashboard.from-savings"),
                        item.fromSavings().format(locale)));
                fromSavings.getStyleClass().add("text-muted");
                fromSavings.setTooltip(new Tooltip(resources.getString("dashboard.from-savings.hint")));
                categoryTable.add(fromSavings, 1, row++, 2, 1);
            }
        }
    }

    /** Spent vs. effective limit; the colour comes from the Category's bar status. */
    private ProgressBar budgetBar(CategorySpending item, Money limit) {
        double progress = Math.clamp((double) item.total().halere() / limit.halere(), 0, 1);
        ProgressBar bar = new ProgressBar(progress);
        bar.setMaxWidth(Double.MAX_VALUE);
        item.barStatus().ifPresent(status ->
                bar.getStyleClass().add("budget-bar-" + status.name().toLowerCase(Locale.ROOT)));
        Tooltip.install(bar, new Tooltip(MessageFormat.format(resources.getString("dashboard.limit.tooltip"),
                item.total().format(locale), limit.format(locale))));
        return bar;
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
