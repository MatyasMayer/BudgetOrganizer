package org.example.budget.storage;

import org.example.budget.domain.SettingsRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class SqliteSettingsRepository implements SettingsRepository {

    private final Database database;

    public SqliteSettingsRepository(Database database) {
        this.database = database;
    }

    @Override
    public Optional<String> find(String key) {
        try (Connection connection = database.connect();
             PreparedStatement query = connection.prepareStatement("SELECT value FROM settings WHERE key = ?")) {
            query.setString(1, key);
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? Optional.of(result.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot read setting " + key, e);
        }
    }

    @Override
    public void save(String key, String value) {
        try (Connection connection = database.connect();
             PreparedStatement upsert = connection.prepareStatement("""
                     INSERT INTO settings (key, value) VALUES (?, ?)
                     ON CONFLICT (key) DO UPDATE SET value = excluded.value""")) {
            upsert.setString(1, key);
            upsert.setString(2, value);
            upsert.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Cannot save setting " + key, e);
        }
    }
}
