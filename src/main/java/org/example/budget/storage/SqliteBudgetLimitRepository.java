package org.example.budget.storage;

import org.example.budget.domain.BudgetLimit;
import org.example.budget.domain.BudgetLimitRepository;
import org.example.budget.domain.Money;
import org.example.budget.domain.Month;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Budget Limits in the {@code budget_limits} table; a NULL month marks the Default Limit. */
public class SqliteBudgetLimitRepository implements BudgetLimitRepository {

    private static final String COLUMNS = "SELECT category_id, month, amount FROM budget_limits";
    private static final String SAME_KEY = "category_id = ? AND month IS ?";

    private final Database database;

    public SqliteBudgetLimitRepository(Database database) {
        this.database = database;
    }

    @Override
    public void save(BudgetLimit limit) {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM budget_limits WHERE " + SAME_KEY);
                 PreparedStatement insert = connection.prepareStatement(
                         "INSERT INTO budget_limits (category_id, month, amount) VALUES (?, ?, ?)")) {
                delete.setLong(1, limit.categoryId());
                delete.setString(2, text(limit.month()));
                delete.executeUpdate();
                insert.setLong(1, limit.categoryId());
                insert.setString(2, text(limit.month()));
                insert.setLong(3, limit.amount().halere());
                insert.executeUpdate();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot save Budget Limit " + limit, e);
        }
    }

    @Override
    public void delete(long categoryId, Optional<Month> month) {
        try (Connection connection = database.connect();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM budget_limits WHERE " + SAME_KEY)) {
            delete.setLong(1, categoryId);
            delete.setString(2, text(month));
            delete.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Cannot delete Budget Limit of Category " + categoryId, e);
        }
    }

    @Override
    public List<BudgetLimit> findByCategory(long categoryId) {
        // NULL (the Default Limit) sorts first; yyyy-MM text sorts chronologically.
        return query(COLUMNS + " WHERE category_id = ? ORDER BY month", query -> query.setLong(1, categoryId));
    }

    @Override
    public List<BudgetLimit> findFor(Month month) {
        return query(COLUMNS + " WHERE month IS NULL OR month = ?",
                query -> query.setString(1, text(Optional.of(month))));
    }

    private List<BudgetLimit> query(String sql, Parameters parameters) {
        try (Connection connection = database.connect();
             PreparedStatement query = connection.prepareStatement(sql)) {
            parameters.set(query);
            try (ResultSet result = query.executeQuery()) {
                List<BudgetLimit> list = new ArrayList<>();
                while (result.next()) {
                    list.add(read(result));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot read Budget Limits", e);
        }
    }

    private static BudgetLimit read(ResultSet row) throws SQLException {
        String month = row.getString("month");
        return new BudgetLimit(
                row.getLong("category_id"),
                Optional.ofNullable(month).map(text -> new Month(YearMonth.parse(text))),
                Money.ofHalere(row.getLong("amount")));
    }

    private static String text(Optional<Month> month) {
        return month.map(Month::toString).orElse(null);
    }

    @FunctionalInterface
    private interface Parameters {
        void set(PreparedStatement query) throws SQLException;
    }
}
