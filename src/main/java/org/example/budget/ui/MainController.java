package org.example.budget.ui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;
import org.example.budget.domain.BudgetService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Function;

/** The main window: a navigation sidebar that swaps the screen shown in the content area. */
public class MainController {

    private final BudgetService service;
    private final Locale locale;

    @FXML
    private ResourceBundle resources;
    @FXML
    private Label currentMonth;
    @FXML
    private ListView<Screen> navigation;
    @FXML
    private StackPane content;

    /** Creates the Controllers that need more than the Model, e.g. Settings; null for all others. */
    private final Function<Class<?>, Object> specialControllers;

    public MainController(BudgetService service, Locale locale) {
        this(service, locale, type -> null);
    }

    public MainController(BudgetService service, Locale locale, Function<Class<?>, Object> specialControllers) {
        this.service = service;
        this.locale = locale;
        this.specialControllers = specialControllers;
    }

    /** Selects a screen in the sidebar and shows it. */
    public void select(Screen screen) {
        navigation.getSelectionModel().select(screen);
    }

    /** The screen shown now. */
    public Screen selected() {
        return navigation.getSelectionModel().getSelectedItem();
    }

    @FXML
    private void initialize() {
        currentMonth.setText(service.currentMonth().firstDay()
                .format(DateTimeFormatter.ofLocalizedPattern("yMMMM").withLocale(locale)));

        navigation.getItems().setAll(Screen.values());
        navigation.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Screen screen, boolean empty) {
                super.updateItem(screen, empty);
                setText(empty || screen == null ? null : resources.getString(screen.titleKey()));
            }
        });
        navigation.getSelectionModel().selectedItemProperty().addListener((obs, previous, screen) -> {
            if (screen != null) {
                show(screen);
            }
        });
        navigation.getSelectionModel().select(Screen.DASHBOARD);
    }

    private void show(Screen screen) {
        try {
            FXMLLoader loader = new FXMLLoader(screen.view(), resources);
            loader.setControllerFactory(this::createController);
            Node view = loader.load();
            content.getChildren().setAll(view);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load view for " + screen, e);
        }
    }

    /** Screen Controllers take {@code (BudgetService, Locale)} when they need the Model, else nothing. */
    private Object createController(Class<?> type) {
        Object special = specialControllers.apply(type);
        if (special != null) {
            return special;
        }
        try {
            try {
                return type.getConstructor(BudgetService.class, Locale.class).newInstance(service, locale);
            } catch (NoSuchMethodException withoutModel) {
                return type.getConstructor().newInstance();
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create controller " + type, e);
        }
    }
}
