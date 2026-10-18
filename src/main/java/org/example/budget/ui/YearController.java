package org.example.budget.ui;

import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.example.budget.domain.BudgetService;
import org.example.budget.domain.Money;
import org.example.budget.domain.MonthOfYear;
import org.example.budget.domain.YearSummary;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * The Year screen: Income vs. Expenses of the 12 Months of a chosen year as a chart, the year totals and average
 * Savings Rate, and a table per Month with Savings-Funded Expenses ("from Savings") and Uncovered Deficits marked.
 */
public class YearController {

    private final BudgetService service;
    private final Locale locale;
    private final DateTimeFormatter shortMonth;
    private final DateTimeFormatter longMonth;

    private int year;

    @FXML
    private ResourceBundle resources;
    @FXML
    private Label yearTitle;
    @FXML
    private VBox content;

    public YearController(BudgetService service, Locale locale) {
        this.service = service;
        this.locale = locale;
        this.shortMonth = DateTimeFormatter.ofPattern("LLL", locale);
        this.longMonth = DateTimeFormatter.ofPattern("LLLL", locale);
    }

    @FXML
    private void initialize() {
        show(service.currentMonth().year());
    }

    @FXML
    private void previousYear() {
        show(year - 1);
    }

    @FXML
    private void nextYear() {
        show(year + 1);
    }

    @FXML
    private void currentYear() {
        show(service.currentMonth().year());
    }

    private void show(int selected) {
        year = selected;
        yearTitle.setText(String.valueOf(year));
        YearSummary summary = service.yearSummary(year);
        content.getChildren().setAll(chart(summary), totals(summary), table(summary));
    }

    private BarChart<String, Number> chart(YearSummary summary) {
        NumberAxis amountAxis = new NumberAxis();
        amountAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number halere) {
                return Money.ofHalere(halere.longValue()).format(locale);
            }

            @Override
            public Number fromString(String text) {
                throw new UnsupportedOperationException();
            }
        });
        BarChart<String, Number> chart = new BarChart<>(new CategoryAxis(), amountAxis);
        chart.setAnimated(false);
        chart.setPrefHeight(320);
        XYChart.Series<String, Number> income = new XYChart.Series<>();
        income.setName(resources.getString("year.income"));
        XYChart.Series<String, Number> expenses = new XYChart.Series<>();
        expenses.setName(resources.getString("year.expenses"));
        for (MonthOfYear month : summary.months()) {
            String name = month.month().firstDay().format(shortMonth);
            income.getData().add(new XYChart.Data<>(name, month.income().halere()));
            expenses.getData().add(new XYChart.Data<>(name, month.expenses().halere()));
        }
        chart.getData().add(income);
        chart.getData().add(expenses);
        for (XYChart.Series<String, Number> series : chart.getData()) {
            for (XYChart.Data<String, Number> bar : series.getData()) {
                Tooltip.install(bar.getNode(), new Tooltip(bar.getXValue() + ", " + series.getName() + ": "
                        + Money.ofHalere(bar.getYValue().longValue()).format(locale)));
            }
        }
        return chart;
    }

    private GridPane totals(YearSummary summary) {
        GridPane totals = new GridPane();
        totals.getStyleClass().add("year-totals");
        totals.setHgap(32);
        totals.setVgap(2);
        Label leftover = value(summary.leftover().format(locale));
        if (summary.leftover().halere() < 0) {
            leftover.getStyleClass().add("danger");
        }
        Label rate = value(summary.averageSavingsRate().map(this::percent)
                .orElse(resources.getString("year.rate.none")));
        rate.setTooltip(new Tooltip(resources.getString("year.rate.hint")));
        totals.addRow(0, muted(resources.getString("year.income")), muted(resources.getString("year.expenses")),
                muted(resources.getString("year.leftover")), muted(resources.getString("year.average-rate")));
        totals.addRow(1, value(summary.income().format(locale)), value(summary.expenses().format(locale)),
                leftover, rate);
        return totals;
    }

    private GridPane table(YearSummary summary) {
        GridPane table = new GridPane();
        table.setHgap(24);
        table.setVgap(6);
        table.addRow(0, muted(resources.getString("year.column.month")),
                amount(muted(resources.getString("year.income"))), amount(muted(resources.getString("year.expenses"))),
                amount(muted(resources.getString("year.leftover"))),
                amount(muted(resources.getString("year.column.rate"))),
                amount(muted(resources.getString("year.column.from-savings"))), new Label());
        int row = 1;
        for (MonthOfYear month : summary.months()) {
            Label leftover = amount(new Label(month.leftover().format(locale)));
            if (month.leftover().halere() < 0) {
                leftover.getStyleClass().add("danger");
            }
            Label fromSavings = amount(new Label(month.fromSavings().halere() == 0
                    ? "" : month.fromSavings().format(locale)));
            if (month.fromSavings().halere() != 0) {
                fromSavings.setTooltip(new Tooltip(resources.getString("year.from-savings.hint")));
            }
            Label marker = new Label();
            if (month.deficit()) {
                marker.setText(resources.getString("year.deficit"));
                marker.getStyleClass().addAll("danger", "year-deficit");
                marker.setTooltip(new Tooltip(resources.getString("dashboard.deficit.hint")));
            }
            table.addRow(row++, new Label(month.month().firstDay().format(longMonth)),
                    amount(new Label(month.income().format(locale))), amount(new Label(month.expenses().format(locale))),
                    leftover, amount(new Label(month.savingsRate().map(this::percent).orElse("-"))), fromSavings,
                    marker);
        }
        return table;
    }

    private String percent(BigDecimal fraction) {
        NumberFormat format = NumberFormat.getPercentInstance(locale);
        format.setMaximumFractionDigits(1);
        return format.format(fraction);
    }

    private static Label muted(String text) {
        return muted(new Label(text));
    }

    private static Label muted(Label label) {
        label.getStyleClass().add("text-muted");
        return label;
    }

    private static Label value(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("title-3");
        return label;
    }

    private static Label amount(Label label) {
        label.setMaxWidth(Double.MAX_VALUE);
        label.setStyle("-fx-alignment: CENTER-RIGHT;");
        GridPane.setHgrow(label, Priority.ALWAYS);
        return label;
    }
}
