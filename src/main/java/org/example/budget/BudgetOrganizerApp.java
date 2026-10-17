package org.example.budget;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.example.budget.currency.CnbRateSource;
import org.example.budget.domain.BudgetService;
import org.example.budget.domain.Clock;
import org.example.budget.domain.Language;
import org.example.budget.storage.Database;
import org.example.budget.storage.SqliteBudgetLimitRepository;
import org.example.budget.storage.SqliteCategoryRepository;
import org.example.budget.storage.SqliteCnbRateRepository;
import org.example.budget.storage.SqliteRecurringTemplateRepository;
import org.example.budget.storage.SqliteSettingsRepository;
import org.example.budget.storage.SqliteTransactionRepository;
import org.example.budget.ui.FirstLaunchController;
import org.example.budget.ui.MainWindow;
import org.example.budget.ui.Messages;

import java.util.Optional;

/** The application bootstrap: wires the real implementations together and opens the main window. */
public class BudgetOrganizerApp extends Application {

    private static final System.Logger LOG = System.getLogger(BudgetOrganizerApp.class.getName());

    @Override
    public void start(Stage stage) throws Exception {
        Database database = Database.openIn(Database.defaultDataDirectory());
        LOG.log(System.Logger.Level.INFO, "Using database {0}", database.file());

        BudgetService service = new BudgetService(
                new SqliteSettingsRepository(database),
                new SqliteCategoryRepository(database),
                new SqliteTransactionRepository(database),
                new SqliteRecurringTemplateRepository(database),
                new SqliteBudgetLimitRepository(database),
                new SqliteCnbRateRepository(database),
                CnbRateSource.overHttp(),
                Clock.system());

        if (service.isFirstLaunch()) {
            MainWindow.applyTheme(service.theme());
            Optional<Language> language = FirstLaunchController.open();
            if (language.isEmpty()) {
                Platform.exit();
                return;
            }
            service.completeFirstLaunch(language.get(), Messages.presetCategoryNames());
        }

        service.generateDueOccurrences();
        new MainWindow(stage, service, database.file()).show();
    }
}
