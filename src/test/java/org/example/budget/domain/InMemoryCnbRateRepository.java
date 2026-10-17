package org.example.budget.domain;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory fake of {@link CnbRateRepository}, kept faithful by {@code CnbRateRepositoryContract}. */
public class InMemoryCnbRateRepository implements CnbRateRepository {

    private record Key(Currency currency, LocalDate date) {
    }

    private final Map<Key, CnbRate> rates = new HashMap<>();

    @Override
    public Optional<CnbRate> find(Currency currency, LocalDate date) {
        return Optional.ofNullable(rates.get(new Key(currency, date)));
    }

    @Override
    public void save(LocalDate date, CnbRate rate) {
        rates.put(new Key(rate.currency(), date), rate);
    }
}
