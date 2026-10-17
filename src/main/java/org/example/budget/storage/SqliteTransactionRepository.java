package org.example.budget.storage;

import org.example.budget.domain.CategoryTotal;
import org.example.budget.domain.Money;
import org.example.budget.domain.Month;
import org.example.budget.domain.Transaction;
import org.example.budget.domain.TransactionDetails;
import org.example.budget.domain.TransactionFilter;
import org.example.budget.domain.TransactionRepository;
import org.example.budget.domain.TransactionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteTransactionRepository implements TransactionRepository {

    private static final String COLUMNS = "SELECT id, date, type, category_id, amount, note FROM transactions";

    private final Database database;

    public SqliteTransactionRepository(Database database) {
        this.database = database;
    }

    @Override
    public Transaction add(TransactionDetails details) {
        try (Connection connection = database.connect();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO transactions (date, type, category_id, amount, note) VALUES (?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            bind(insert, details);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                keys.next();
                return new Transaction(keys.getLong(1), details);
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot add Transaction", e);
        }
    }

    @Override
    public Optional<Transaction> find(long id) {
        try (Connection connection = database.connect();
             PreparedStatement query = connection.prepareStatement(COLUMNS + " WHERE id = ?")) {
            query.setLong(1, id);
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot read Transaction " + id, e);
        }
    }

    @Override
    public void update(Transaction transaction) {
        try (Connection connection = database.connect();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE transactions SET date = ?, type = ?, category_id = ?, amount = ?, note = ? WHERE id = ?")) {
            bind(update, transaction.details());
            update.setLong(6, transaction.id());
            update.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Cannot update Transaction " + transaction.id(), e);
        }
    }

    @Override
    public void delete(long id) {
        try (Connection connection = database.connect();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM transactions WHERE id = ?")) {
            delete.setLong(1, id);
            delete.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Cannot delete Transaction " + id, e);
        }
    }

    @Override
    public void moveToCategory(long fromCategoryId, long toCategoryId) {
        try (Connection connection = database.connect();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE transactions SET category_id = ? WHERE category_id = ?")) {
            update.setLong(1, toCategoryId);
            update.setLong(2, fromCategoryId);
            update.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Cannot move Transactions of Category " + fromCategoryId, e);
        }
    }

    /**
     * Month, Category and type are filtered in SQL. The Note search runs in Java because SQLite's
     * {@code LIKE} ignores case only for ASCII letters, and Notes are often Czech.
     */
    @Override
    public List<Transaction> find(TransactionFilter filter) {
        StringBuilder sql = new StringBuilder(COLUMNS).append(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        filter.month().ifPresent(month -> {
            sql.append(" AND date BETWEEN ? AND ?");
            parameters.add(month.firstDay().toString());
            parameters.add(month.lastDay().toString());
        });
        filter.categoryId().ifPresent(id -> {
            sql.append(" AND category_id = ?");
            parameters.add(id);
        });
        filter.type().ifPresent(type -> {
            sql.append(" AND type = ?");
            parameters.add(type.name());
        });
        sql.append(" ORDER BY date DESC, id DESC");

        try (Connection connection = database.connect();
             PreparedStatement query = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < parameters.size(); i++) {
                query.setObject(i + 1, parameters.get(i));
            }
            List<Transaction> list = new ArrayList<>();
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    Transaction transaction = read(result);
                    if (filter.noteMatches(transaction.note())) {
                        list.add(transaction);
                    }
                }
            }
            return list;
        } catch (SQLException e) {
            throw new StorageException("Cannot list Transactions", e);
        }
    }

    @Override
    public List<CategoryTotal> categoryTotals(Month month) {
        try (Connection connection = database.connect();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT category_id, type, SUM(amount) AS total FROM transactions"
                             + " WHERE date BETWEEN ? AND ? GROUP BY category_id, type")) {
            query.setString(1, month.firstDay().toString());
            query.setString(2, month.lastDay().toString());
            List<CategoryTotal> totals = new ArrayList<>();
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    totals.add(new CategoryTotal(result.getLong("category_id"),
                            TransactionType.valueOf(result.getString("type")),
                            Money.ofHalere(result.getLong("total"))));
                }
            }
            return totals;
        } catch (SQLException e) {
            throw new StorageException("Cannot total the Categories of " + month, e);
        }
    }

    private static void bind(PreparedStatement statement, TransactionDetails details) throws SQLException {
        statement.setString(1, details.date().toString());
        statement.setString(2, details.type().name());
        statement.setLong(3, details.categoryId());
        statement.setLong(4, details.amount().halere());
        statement.setString(5, details.hasNote() ? details.note() : null);
    }

    private static Transaction read(ResultSet row) throws SQLException {
        return new Transaction(row.getLong("id"), new TransactionDetails(
                LocalDate.parse(row.getString("date")),
                TransactionType.valueOf(row.getString("type")),
                row.getLong("category_id"),
                Money.ofHalere(row.getLong("amount")),
                row.getString("note")));
    }
}
