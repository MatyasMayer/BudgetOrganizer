package org.example.budget.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Applies numbered SQL scripts ({@code V<number>__<description>.sql}) from a classpath directory in
 * numeric order, each in its own transaction, and records every applied version in {@code schema_version}.
 */
public class MigrationRunner {

    public static final String APP_MIGRATIONS = "org/example/budget/storage/migration";

    private static final Pattern SCRIPT_NAME = Pattern.compile("V(\\d+)__(\\w+)\\.sql");

    private final String location;

    public MigrationRunner(String classpathLocation) {
        this.location = classpathLocation;
    }

    public static MigrationRunner forApp() {
        return new MigrationRunner(APP_MIGRATIONS);
    }

    /** Applies all pending migrations and returns their versions, in the order applied. */
    public List<Integer> migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version     INTEGER PRIMARY KEY,
                        description TEXT NOT NULL,
                        applied_at  TEXT NOT NULL
                    )""");
        }
        int current = currentVersion(connection);
        List<Integer> applied = new ArrayList<>();
        for (Migration migration : loadMigrations()) {
            if (migration.version() > current) {
                apply(connection, migration);
                applied.add(migration.version());
            }
        }
        return applied;
    }

    /** The highest applied version, or 0 for a database that has never been migrated. */
    public int currentVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet tables = statement.executeQuery(
                     "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'schema_version'")) {
            if (!tables.next()) {
                return 0;
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            result.next();
            return result.getInt(1);
        }
    }

    private static void apply(Connection connection, Migration migration) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (Statement statement = connection.createStatement()) {
                for (String sql : migration.statements()) {
                    statement.executeUpdate(sql);
                }
            }
            try (PreparedStatement record = connection.prepareStatement(
                    "INSERT INTO schema_version (version, description, applied_at) VALUES (?, ?, ?)")) {
                record.setInt(1, migration.version());
                record.setString(2, migration.description());
                record.setString(3, Instant.now().toString());
                record.executeUpdate();
            }
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private List<Migration> loadMigrations() {
        URL url = MigrationRunner.class.getClassLoader().getResource(location);
        if (url == null) {
            throw new IllegalStateException("Migration directory not found on classpath: " + location);
        }
        try {
            URI uri = url.toURI();
            Path directory = "jar".equals(uri.getScheme())
                    ? jarFileSystem(uri).getPath(location)
                    : Path.of(uri);
            return loadFrom(directory);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static FileSystem jarFileSystem(URI uri) throws IOException {
        try {
            return FileSystems.getFileSystem(uri);
        } catch (FileSystemNotFoundException e) {
            return FileSystems.newFileSystem(uri, Map.of());
        }
    }

    private static List<Migration> loadFrom(Path directory) throws IOException {
        List<Migration> migrations = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.toList()) {
                Matcher name = SCRIPT_NAME.matcher(file.getFileName().toString());
                if (name.matches()) {
                    migrations.add(new Migration(
                            Integer.parseInt(name.group(1)),
                            name.group(2),
                            Files.readString(file, StandardCharsets.UTF_8)));
                }
            }
        }
        migrations.sort(Comparator.comparingInt(Migration::version));
        return migrations;
    }

    private record Migration(int version, String description, String script) {

        /** Splits on ';'. Enough for hand-written scripts without triggers or ';' inside string literals. */
        List<String> statements() {
            String withoutComments = script.lines()
                    .filter(line -> !line.strip().startsWith("--"))
                    .collect(Collectors.joining("\n"));
            return Arrays.stream(withoutComments.split(";"))
                    .map(String::strip)
                    .filter(sql -> !sql.isEmpty())
                    .toList();
        }
    }
}
