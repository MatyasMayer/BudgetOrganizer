package org.example.budget.storage;

import org.example.budget.domain.CnbRate;
import org.example.budget.domain.CnbRateRepository;
import org.example.budget.domain.Currency;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

public class SqliteCnbRateRepository implements CnbRateRepository {

    private final Database database;

    public SqliteCnbRateRepository(Database database) {
        this.database = database;
    }

    @Override
    public Optional<CnbRate> find(Currency currency, LocalDate date) {
        try (Connection connection = database.connect();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT valid_for, czk_per_unit FROM cnb_rates WHERE date = ? AND currency = ?")) {
            query.setString(1, date.toString());
            query.setString(2, currency.code());
            try (ResultSet result = query.executeQuery()) {
                return result.next()
                        ? Optional.of(new CnbRate(currency, LocalDate.parse(result.getString("valid_for")),
                                new BigDecimal(result.getString("czk_per_unit"))))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot read the cached " + currency + " rate for " + date, e);
        }
    }

    @Override
    public void save(LocalDate date, CnbRate rate) {
        try (Connection connection = database.connect();
             PreparedStatement upsert = connection.prepareStatement("""
                     INSERT INTO cnb_rates (date, currency, valid_for, czk_per_unit) VALUES (?, ?, ?, ?)
                     ON CONFLICT (date, currency) DO UPDATE
                     SET valid_for = excluded.valid_for, czk_per_unit = excluded.czk_per_unit""")) {
            upsert.setString(1, date.toString());
            upsert.setString(2, rate.currency().code());
            upsert.setString(3, rate.validFor().toString());
            upsert.setString(4, rate.czkPerUnit().toPlainString());
            upsert.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Cannot cache the " + rate.currency() + " rate for " + date, e);
        }
    }
}
