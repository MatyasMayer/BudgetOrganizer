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

    public MainController(BudgetService service, Locale locale) {
        this.service = service;
        this.locale = locale;
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
            Node view = FXMLLoader.load(screen.view(), resources);
            content.getChildren().setAll(view);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load view for " + screen, e);
        }
    }
}
