package org.example.budget.storage;

import org.example.budget.domain.Frequency;
import org.example.budget.domain.Money;
import org.example.budget.domain.RecurringTemplate;
import org.example.budget.domain.RecurringTemplateDetails;
import org.example.budget.domain.RecurringTemplateRepository;
import org.example.budget.domain.TransactionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SqliteRecurringTemplateRepository implements RecurringTemplateRepository {

    private static final String COLUMNS = "SELECT id, type, category_id, amount, note, frequency, day_of_month,"
            + " start_date, end_date FROM recurring_templates";

    private final Database database;

    public SqliteRecurringTemplateRepository(Database database) {
        this.database = database;
    }

    @Override
    public RecurringTemplate add(RecurringTemplateDetails details) {
        try (Connection connection = database.connect();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO recurring_templates (type, category_id, amount, note, frequency, day_of_month,"
                             + " start_date, end_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, details.type().name());
            insert.setLong(2, details.categoryId());
            insert.setLong(3, details.amount().halere());
            insert.setString(4, details.hasNote() ? details.note() : null);
            insert.setString(5, details.frequency().name());
            insert.setInt(6, details.dayOfMonth());
            insert.setString(7, details.startDate().toString());
            insert.setString(8, details.endDate().map(LocalDate::toString).orElse(null));
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                keys.next();
                return new RecurringTemplate(keys.getLong(1), details);
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot add Recurring Template", e);
        }
    }

    @Override
    public Optional<RecurringTemplate> find(long id) {
        try (Connection connection = database.connect();
             PreparedStatement query = connection.prepareStatement(COLUMNS + " WHERE id = ?")) {
            query.setLong(1, id);
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot read Recurring Template " + id, e);
        }
    }

    @Override
    public void update(RecurringTemplate template, LocalDate editedOn) {
        RecurringTemplateDetails details = template.details();
        try (Connection connection = database.connect();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE recurring_templates SET type = ?, category_id = ?, amount = ?, note = ?, frequency = ?,"
                             + " day_of_month = ?, start_date = ?, end_date = ?, edited_on = ? WHERE id = ?")) {
            update.setString(1, details.type().name());
            update.setLong(2, details.categoryId());
            update.setLong(3, details.amount().halere());
            update.setString(4, details.hasNote() ? details.note() : null);
            update.setString(5, details.frequency().name());
            update.setInt(6, details.dayOfMonth());
            update.setString(7, details.startDate().toString());
            update.setString(8, details.endDate().map(LocalDate::toString).orElse(null));
            update.setString(9, editedOn.toString());
            update.setLong(10, template.id());
            update.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Cannot update Recurring Template " + template.id(), e);
        }
    }

    @Override
    public Optional<LocalDate> lastEdited(long templateId) {
        try (Connection connection = database.connect();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT edited_on FROM recurring_templates WHERE id = ?")) {
            query.setLong(1, templateId);
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? Optional.ofNullable(result.getString(1)).map(LocalDate::parse) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot read Recurring Template " + templateId, e);
        }
    }

    @Override
    public List<RecurringTemplate> findAll() {
        try (Connection connection = database.connect();
             PreparedStatement query = connection.prepareStatement(COLUMNS + " ORDER BY id");
             ResultSet result = query.executeQuery()) {
            List<RecurringTemplate> list = new ArrayList<>();
            while (result.next()) {
                list.add(read(result));
            }
            return list;
        } catch (SQLException e) {
            throw new StorageException("Cannot list Recurring Templates", e);
        }
    }

    @Override
    public void moveToCategory(long fromCategoryId, long toCategoryId) {
        try (Connection connection = database.connect();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE recurring_templates SET category_id = ? WHERE category_id = ?")) {
            update.setLong(1, toCategoryId);
            update.setLong(2, fromCategoryId);
            update.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Cannot move Recurring Templates of Category " + fromCategoryId, e);
        }
    }

    @Override
    public void addOccurrence(long templateId, LocalDate occurrenceDate, long transactionId) {
        try (Connection connection = database.connect();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO generated_occurrences (template_id, occurrence_date, transaction_id) VALUES (?, ?, ?)")) {
            insert.setLong(1, templateId);
            insert.setString(2, occurrenceDate.toString());
            insert.setLong(3, transactionId);
            insert.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Cannot record occurrence " + occurrenceDate + " of template " + templateId, e);
        }
    }

    @Override
    public Optional<LocalDate> lastOccurrence(long templateId) {
        try (Connection connection = database.connect();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT MAX(occurrence_date) FROM generated_occurrences WHERE template_id = ?")) {
            query.setLong(1, templateId);
            try (ResultSet result = query.executeQuery()) {
                result.next();
                return Optional.ofNullable(result.getString(1)).map(LocalDate::parse);
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot read occurrences of template " + templateId, e);
        }
    }

    @Override
    public Set<Long> generatedTransactionIds() {
        try (Connection connection = database.connect();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT transaction_id FROM generated_occurrences WHERE transaction_id IS NOT NULL");
             ResultSet result = query.executeQuery()) {
            Set<Long> ids = new HashSet<>();
            while (result.next()) {
                ids.add(result.getLong(1));
            }
            return Set.copyOf(ids);
        } catch (SQLException e) {
            throw new StorageException("Cannot list generated Transactions", e);
        }
    }

    private static RecurringTemplate read(ResultSet row) throws SQLException {
        return new RecurringTemplate(row.getLong("id"), new RecurringTemplateDetails(
                TransactionType.valueOf(row.getString("type")),
                row.getLong("category_id"),
                Money.ofHalere(row.getLong("amount")),
                row.getString("note"),
                Frequency.valueOf(row.getString("frequency")),
                row.getInt("day_of_month"),
                LocalDate.parse(row.getString("start_date")),
                Optional.ofNullable(row.getString("end_date")).map(LocalDate::parse)));
    }
}
