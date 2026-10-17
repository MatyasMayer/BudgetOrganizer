package org.example.budget;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.budget.currency.CnbRateSource;
import org.example.budget.domain.BudgetService;
import org.example.budget.domain.Clock;
import org.example.budget.storage.Database;
import org.example.budget.storage.SqliteCategoryRepository;
import org.example.budget.storage.SqliteCnbRateRepository;
import org.example.budget.storage.SqliteSettingsRepository;
import org.example.budget.storage.SqliteTransactionRepository;
import org.example.budget.ui.MainController;
import org.example.budget.ui.Messages;

import java.util.Locale;
import java.util.ResourceBundle;

/** The application bootstrap: wires the real implementations together and opens the main window. */
public class BudgetOrganizerApp extends Application {

    private static final System.Logger LOG = System.getLogger(BudgetOrganizerApp.class.getName());

    /** Development override of the UI language, e.g. {@code -Dbudget.lang=cs}; English by default. */
    private static final String LANGUAGE_PROPERTY = "budget.lang";

    @Override
    public void start(Stage stage) throws Exception {
        Database database = Database.openIn(Database.defaultDataDirectory());
        LOG.log(System.Logger.Level.INFO, "Using database {0}", database.file());

        BudgetService service = new BudgetService(
                new SqliteSettingsRepository(database),
                new SqliteCategoryRepository(database),
                new SqliteTransactionRepository(database),
                new SqliteCnbRateRepository(database),
                CnbRateSource.overHttp(),
                Clock.system());
        Locale locale = Locale.forLanguageTag(System.getProperty(LANGUAGE_PROPERTY, "en"));
        ResourceBundle messages = Messages.bundle(locale);
        service.seedPresetCategories(preset -> messages.getString(Messages.presetCategoryKey(preset)));

        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        FXMLLoader loader = new FXMLLoader(MainController.class.getResource("view/main.fxml"), messages);
        loader.setControllerFactory(type -> {
            if (type == MainController.class) {
                return new MainController(service, locale);
            }
            throw new IllegalArgumentException("Unknown controller " + type);
        });
        Parent root = loader.load();

        stage.setTitle(messages.getString("app.title"));
        stage.setScene(new Scene(root));
        stage.show();
    }
}
