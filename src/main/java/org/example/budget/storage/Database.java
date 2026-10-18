package org.example.budget.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** The SQLite file {@code budget.db}, migrated to the latest schema when opened. */
public final class Database {

    public static final String FILE_NAME = "budget.db";

    /** System property overriding the data directory, so development runs don't touch the real data. */
    public static final String DATA_DIR_PROPERTY = "budget.dataDir";

    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final Path file;

    private Database(Path file) {
        this.file = file;
    }

    /** Creates the directory and the database if missing, then applies pending migrations. */
    public static Database openIn(Path dataDirectory) throws SQLException {
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create data directory " + dataDirectory, e);
        }
        Database database = new Database(dataDirectory.resolve(FILE_NAME));
        try (Connection connection = database.connect()) {
            MigrationRunner.forApp().migrate(connection);
        }
        return database;
    }

    /** {@code %APPDATA%\BudgetOrganizer}, unless overridden by the {@value #DATA_DIR_PROPERTY} system property. */
    public static Path defaultDataDirectory() {
        String override = System.getProperty(DATA_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        String appData = System.getenv("APPDATA");
        Path base = appData != null ? Path.of(appData) : Path.of(System.getProperty("user.home"));
        return base.resolve("BudgetOrganizer");
    }

    public Path file() {
        return file;
    }

    /**
     * Writes a consistent copy of the database to {@code budget-yyyy-MM-dd_HH-mm-ss.db} in the folder (created
     * if missing) and returns its path. It never overwrites an existing file.
     */
    public Path backupTo(Path folder, LocalDateTime now) {
        Path target = folder.resolve("budget-" + BACKUP_STAMP.format(now) + ".db");
        try {
            Files.createDirectories(folder);
            if (Files.exists(target)) {
                throw new StorageException("The backup " + target + " already exists", null);
            }
            try (Connection connection = connect(); PreparedStatement copy = connection.prepareStatement("VACUUM INTO ?")) {
                copy.setString(1, target.toString());
                copy.execute();
            }
        } catch (IOException | SQLException e) {
            throw new StorageException("Cannot back up the database to " + target, e);
        }
        return target;
    }

    public Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }
}
