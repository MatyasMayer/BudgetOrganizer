package org.example.budget.ui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import org.example.budget.domain.BudgetService;
import org.example.budget.domain.Category;
import org.example.budget.domain.Frequency;
import org.example.budget.domain.InvalidRecurringTemplateException;
import org.example.budget.domain.RecurringTemplate;
import org.example.budget.domain.RecurringTemplateDetails;
import org.example.budget.domain.TransactionType;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The Recurring screen: lists the Recurring Templates (active or ended), creates new ones, edits them and ends
 * them. There is no delete: a template is ended with an end date so its history stays linked.
 */
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
    private TableColumn<RecurringTemplate, String> statusColumn;
    @FXML
    private TableColumn<RecurringTemplate, String> noteColumn;
    @FXML
    private Button editButton;
    @FXML
    private Button endButton;
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
        amountColumn.setCellValueFactory(row -> text(details(row).amount().format(locale)));
        repeatsColumn.setCellValueFactory(row -> text(repeats(details(row))));
        startColumn.setCellValueFactory(row -> text(details(row).startDate().format(dateFormat)));
        endColumn.setCellValueFactory(row -> text(details(row).endDate()
                .map(dateFormat::format)
                .orElseGet(() -> resources.getString("recurring.no-end"))));
        statusColumn.setCellValueFactory(row -> text(resources.getString(
                details(row).hasEndedBy(service.today()) ? "recurring.status.ended" : "recurring.status.active")));
        noteColumn.setCellValueFactory(row -> text(details(row).note()));
        table.setRowFactory(view -> {
            TableRow<RecurringTemplate> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    edit(row.getItem());
                }
            });
            return row;
        });
        editButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        endButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        refresh();
    }

    @FXML
    private void newTemplate() {
        if (RecurringTemplateEditorController.open(table.getScene().getWindow(), service, resources, locale)) {
            generateAndRefresh();
        }
    }

    @FXML
    private void editSelected() {
        Optional.ofNullable(table.getSelectionModel().getSelectedItem()).ifPresent(this::edit);
    }

    private void edit(RecurringTemplate template) {
        if (RecurringTemplateEditorController.open(table.getScene().getWindow(), service, resources, locale,
                Optional.of(template))) {
            generateAndRefresh();
        }
    }

    /** Asks for an end date (today by default) and ends the selected template; it is never deleted. */
    @FXML
    private void endSelected() {
        RecurringTemplate selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        DatePicker endDate = new DatePicker(selected.details().endDate().orElse(service.today()));
        endDate.setConverter(new TransactionEditorController.LocalizedDateConverter(locale));
        Label error = new Label();
        error.getStyleClass().add("danger");
        error.setWrapText(true);
        Label explanation = new Label(resources.getString("recurring.end.content"));
        explanation.setWrapText(true);
        VBox content = new VBox(10, explanation, new Label(resources.getString("recurring.end.date")), endDate, error);
        content.setPrefWidth(420);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(table.getScene().getWindow());
        dialog.setTitle(resources.getString("recurring.end.title"));
        dialog.setHeaderText(MessageFormat.format(resources.getString("recurring.end.header"), describe(selected)));
        ButtonType end = new ButtonType(resources.getString("recurring.end"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(end, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(content);
        Button endButtonInDialog = (Button) dialog.getDialogPane().lookupButton(end);
        endButtonInDialog.addEventFilter(ActionEvent.ACTION, event -> {
            Optional<LocalDate> chosen = typedDate(endDate);
            if (chosen.isEmpty()) {
                error.setText(resources.getString("editor.error.date"));
                event.consume();
                return;
            }
            try {
                service.endRecurringTemplate(selected.id(), chosen.get());
            } catch (InvalidRecurringTemplateException e) {
                error.setText(resources.getString(Messages.recurringProblemKey(e.problem())));
                event.consume();
            }
        });
        if (dialog.showAndWait().filter(end::equals).isPresent()) {
            refresh();
        }
    }

    /** The date as typed, even if the picker hasn't committed it; empty when blank or unreadable. */
    private static Optional<LocalDate> typedDate(DatePicker picker) {
        String text = picker.getEditor().getText();
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(picker.getConverter().fromString(text));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** The template's Note, or its Category's name when it has none. */
    private String describe(RecurringTemplate template) {
        RecurringTemplateDetails details = template.details();
        return details.hasNote() ? details.note() : categoryName(details.categoryId());
    }

    private void generateAndRefresh() {
        // A template starting in the past catches up right away, as it would on the next start.
        service.generateDueOccurrences();
        refresh();
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
