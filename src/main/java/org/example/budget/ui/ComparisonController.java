package org.example.budget.ui;

import javafx.fxml.FXML;
import javafx.scene.Node;
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
import org.example.budget.domain.CategoryChange;
import org.example.budget.domain.Change;
import org.example.budget.domain.Comparison;
import org.example.budget.domain.Money;
import org.example.budget.domain.Month;
import org.example.budget.domain.MonthComparison;

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * The Comparison screen: a Month's Expenses per Category against the previous Month, as a grouped bar chart and a
 * table with the difference in CZK and %. For the running Month the Same-Point Comparison comes first and the
 * whole Months below it.
 */
public class ComparisonController {

    private final BudgetService service;
    private final Locale locale;
    private final DateTimeFormatter monthFormat;
    private final DateTimeFormatter dateFormat;

    private Month month;

    @FXML
    private ResourceBundle resources;
    @FXML
    private Label monthTitle;
    @FXML
    private VBox sections;

    public ComparisonController(BudgetService service, Locale locale) {
        this.service = service;
        this.locale = locale;
        this.monthFormat = DateTimeFormatter.ofLocalizedPattern("yMMMM").withLocale(locale);
        this.dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
    }

    @FXML
    private void initialize() {
        show(service.currentMonth());
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

    private void show(Month selected) {
        month = selected;
        monthTitle.setText(month.firstDay().format(monthFormat));
        MonthComparison comparison = service.monthComparison(month);
        sections.getChildren().clear();
        comparison.samePoint().ifPresent(samePoint -> sections.getChildren().add(section(
                "comparison.same-point", Optional.of(resources.getString("comparison.same-point.hint")), samePoint)));
        sections.getChildren().add(section("comparison.full-month", Optional.empty(), comparison.fullMonth()));
    }

    private Node section(String titleKey, Optional<String> hint, Comparison comparison) {
        VBox section = new VBox();
        section.getStyleClass().add("comparison-section");
        Label heading = new Label(resources.getString(titleKey));
        heading.getStyleClass().add("title-4");
        hint.ifPresent(text -> heading.setTooltip(new Tooltip(text)));
        String current = period(comparison.from(), comparison.through());
        String previous = period(comparison.previousFrom(), comparison.previousThrough());
        Label periods = new Label(MessageFormat.format(resources.getString("comparison.periods"), current, previous));
        periods.getStyleClass().add("text-muted");
        section.getChildren().addAll(heading, periods);
        if (comparison.categories().isEmpty()) {
            Label empty = new Label(resources.getString("comparison.no-expenses"));
            empty.getStyleClass().add("text-muted");
            section.getChildren().add(empty);
        } else {
            section.getChildren().addAll(chart(comparison, current, previous), table(comparison, current, previous));
        }
        return section;
    }

    /** Grouped bars: per Category, the compared period next to the previous one. */
    private BarChart<String, Number> chart(Comparison comparison, String currentName, String previousName) {
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
        XYChart.Series<String, Number> current = new XYChart.Series<>();
        current.setName(currentName);
        XYChart.Series<String, Number> previous = new XYChart.Series<>();
        previous.setName(previousName);
        for (CategoryChange category : comparison.categories()) {
            String name = category.category().name();
            current.getData().add(new XYChart.Data<>(name, category.change().current().halere()));
            previous.getData().add(new XYChart.Data<>(name, category.change().previous().halere()));
        }
        chart.getData().add(current);
        chart.getData().add(previous);
        for (XYChart.Series<String, Number> series : chart.getData()) {
            for (XYChart.Data<String, Number> bar : series.getData()) {
                Tooltip.install(bar.getNode(), new Tooltip(bar.getXValue() + ", " + series.getName() + ": "
                        + Money.ofHalere(bar.getYValue().longValue()).format(locale)));
            }
        }
        return chart;
    }

    private GridPane table(Comparison comparison, String currentName, String previousName) {
        GridPane table = new GridPane();
        table.setHgap(24);
        table.setVgap(6);
        table.addRow(0, muted(new Label(resources.getString("comparison.category"))),
                muted(amount(currentName)), muted(amount(previousName)),
                muted(amount(resources.getString("comparison.difference"))),
                muted(amount(resources.getString("comparison.percent"))));
        int row = 1;
        for (CategoryChange category : comparison.categories()) {
            addRow(table, row++, category.category().name(), category.change(), false);
        }
        addRow(table, row, resources.getString("comparison.total"), comparison.total(), true);
        return table;
    }

    /** More spending than before is shown in red, less in green. */
    private void addRow(GridPane table, int row, String name, Change change, boolean total) {
        Label difference = amount(signed(change.difference()));
        Label percent = amount(change.percent().map(this::percent)
                .orElse(resources.getString("comparison.percent.none")));
        long delta = change.difference().halere();
        if (delta != 0) {
            String style = delta > 0 ? "danger" : "success";
            difference.getStyleClass().add(style);
            percent.getStyleClass().add(style);
        }
        Label[] cells = {new Label(name), amount(change.current().format(locale)),
                amount(change.previous().format(locale)), difference, percent};
        if (total) {
            for (Label cell : cells) {
                cell.getStyleClass().add("comparison-total");
            }
        }
        table.addRow(row, cells);
    }

    private static Label muted(Label label) {
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

    private String signed(Money money) {
        return (money.halere() > 0 ? "+" : "") + money.format(locale);
    }

    private String percent(BigDecimal fraction) {
        NumberFormat format = NumberFormat.getPercentInstance(locale);
        format.setMaximumFractionDigits(1);
        return (fraction.signum() > 0 ? "+" : "") + format.format(fraction);
    }

    private String period(LocalDate from, LocalDate through) {
        return MessageFormat.format(resources.getString("comparison.period"),
                from.format(dateFormat), through.format(dateFormat));
    }
}
