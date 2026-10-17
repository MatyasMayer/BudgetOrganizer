package org.example.budget.storage;

import org.example.budget.domain.Category;
import org.example.budget.domain.CategoryRepository;
import org.example.budget.domain.TransactionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteCategoryRepository implements CategoryRepository {

    private static final String COLUMNS = "SELECT id, name, type, system FROM categories";

    private final Database database;

    public SqliteCategoryRepository(Database database) {
        this.database = database;
    }

    @Override
    public Category add(String name, TransactionType type, boolean system) {
        try (Connection connection = database.connect();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO categories (name, type, system) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, name);
            insert.setString(2, type.name());
            insert.setInt(3, system ? 1 : 0);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                keys.next();
                return new Category(keys.getLong(1), name, type, system);
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot add Category " + name, e);
        }
    }

    @Override
    public Optional<Category> find(long id) {
        try (Connection connection = database.connect();
             PreparedStatement query = connection.prepareStatement(COLUMNS + " WHERE id = ?")) {
            query.setLong(1, id);
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot read Category " + id, e);
        }
    }

    @Override
    public List<Category> findAll() {
        try (Connection connection = database.connect();
             PreparedStatement query = connection.prepareStatement(COLUMNS + " ORDER BY id");
             ResultSet result = query.executeQuery()) {
            List<Category> list = new ArrayList<>();
            while (result.next()) {
                list.add(read(result));
            }
            return list;
        } catch (SQLException e) {
            throw new StorageException("Cannot read Categories", e);
        }
    }

    private static Category read(ResultSet row) throws SQLException {
        return new Category(
                row.getLong("id"),
                row.getString("name"),
                TransactionType.valueOf(row.getString("type")),
                row.getInt("system") == 1);
    }
}
