package org.example.budget.ui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.example.budget.domain.BudgetService;
import org.example.budget.domain.Category;
import org.example.budget.domain.Frequency;
import org.example.budget.domain.RecurringTemplate;
import org.example.budget.domain.RecurringTemplateDetails;
import org.example.budget.domain.TransactionType;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The Recurring screen: lists the Recurring Templates and creates new ones. */
public class RecurringController {

    private final BudgetService service;
    private final Locale locale;
    private final DateTimeFormatter dateFormat;
    private final DateTimeFormatter dayAndMonthFormat;

    private Map<Long, Category> categoriesById = Map.of();

    @FXML
    private ResourceBundle resources;
    @FXML
    private TableView<RecurringTemplate> table;
    @FXML
    private TableColumn<RecurringTemplate, String> typeColumn;
    @FXML
    private TableColumn<RecurringTemplate, String> categoryColumn;
    @FXML
    private TableColumn<RecurringTemplate, String> amountColumn;
    @FXML
    private TableColumn<RecurringTemplate, String> repeatsColumn;
    @FXML
    private TableColumn<RecurringTemplate, String> startColumn;
    @FXML
    private TableColumn<RecurringTemplate, String> endColumn;
    @FXML
    private TableColumn<RecurringTemplate, String> noteColumn;
    @FXML
    private Label summary;

    public RecurringController(BudgetService service, Locale locale) {
        this.service = service;
        this.locale = locale;
        this.dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
        this.dayAndMonthFormat = DateTimeFormatter.ofLocalizedPattern("MMMMd").withLocale(locale);
    }

    @FXML
    private void initialize() {
        typeColumn.setCellValueFactory(row -> text(resources.getString(Messages.typeKey(details(row).type()))));
        categoryColumn.setCellValueFactory(row -> text(categoryName(details(row).categoryId())));
        amountColumn.setCellValueFactory(row -> text(details(row).originalAmount()
                .map(original -> original.format(locale))
                .orElseGet(() -> details(row).amount().format(locale))));
        repeatsColumn.setCellValueFactory(row -> text(repeats(details(row))));
        startColumn.setCellValueFactory(row -> text(details(row).startDate().format(dateFormat)));
        endColumn.setCellValueFactory(row -> text(details(row).endDate()
                .map(dateFormat::format)
                .orElseGet(() -> resources.getString("recurring.no-end"))));
        noteColumn.setCellValueFactory(row -> text(details(row).note()));
        refresh();
    }

    @FXML
    private void newTemplate() {
        if (RecurringTemplateEditorController.open(table.getScene().getWindow(), service, resources, locale)) {
            // A template starting in the past catches up right away, as it would on the next start.
            service.generateDueOccurrences();
            refresh();
        }
    }

    private void refresh() {
        categoriesById = Stream.of(TransactionType.values())
                .flatMap(type -> service.categories(type).stream())
                .collect(Collectors.toMap(Category::id, Function.identity()));
        List<RecurringTemplate> templates = service.recurringTemplates();
        table.getItems().setAll(templates);
        summary.setText(MessageFormat.format(resources.getString("recurring.count"), templates.size()));
    }

    /** E.g. "Monthly on day 31" or "Yearly on March 15". */
    private String repeats(RecurringTemplateDetails details) {
        if (details.frequency() == Frequency.MONTHLY) {
            return MessageFormat.format(resources.getString("recurring.repeats.monthly"), details.dayOfMonth());
        }
        YearMonth month = YearMonth.from(details.startDate());
        LocalDate first = month.atDay(Math.min(details.dayOfMonth(), month.lengthOfMonth()));
        return MessageFormat.format(resources.getString("recurring.repeats.yearly"), first.format(dayAndMonthFormat));
    }

    private String categoryName(long id) {
        Category category = categoriesById.get(id);
        return category == null ? "" : category.name();
    }

    private static RecurringTemplateDetails details(TableColumn.CellDataFeatures<RecurringTemplate, String> row) {
        return row.getValue().details();
    }

    private static ReadOnlyStringWrapper text(String value) {
        return new ReadOnlyStringWrapper(value);
    }
}
