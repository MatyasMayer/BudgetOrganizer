package org.example.budget.domain;

import java.time.LocalDate;
import java.util.Optional;

/**
 * The cache of fetched ČNB Rates, by date and currency, so each rate is downloaded only once. The rate
 * stored for a date may belong to an earlier fixing (a weekend or holiday keeps the previous working day's).
 */
public interface CnbRateRepository {

    Optional<CnbRate> find(Currency currency, LocalDate date);

    /** Stores the rate to use for {@code date}, replacing any rate cached for that date and currency. */
    void save(LocalDate date, CnbRate rate);

    /** The cached rate with the latest fixing date: the Provisional Rate used when ČNB can't be reached. */
    Optional<CnbRate> mostRecent(Currency currency);
}
