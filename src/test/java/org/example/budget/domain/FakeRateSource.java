package org.example.budget.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A fake ČNB: answers with fixed fixings and, like ČNB, gives the latest fixing on or before the asked date
 * (weekends, holidays, not yet published). Counts requests, and can be switched offline.
 */
public class FakeRateSource implements RateSource {

    private final Map<Currency, TreeMap<LocalDate, BigDecimal>> fixings = new TreeMap<>();
    private boolean offline;
    private int requests;

    /** Publishes a fixing: CZK per unit of the currency, valid from {@code date}. */
    public FakeRateSource publish(Currency currency, LocalDate date, String czkPerUnit) {
        fixings.computeIfAbsent(currency, c -> new TreeMap<>()).put(date, new BigDecimal(czkPerUnit));
        return this;
    }

    public void goOffline() {
        offline = true;
    }

    /** How many times the rate source was asked, i.e. network calls in the real app. */
    public int requests() {
        return requests;
    }

    @Override
    public Optional<CnbRate> fetch(Currency currency, LocalDate date) {
        requests++;
        if (offline) {
            return Optional.empty();
        }
        return Optional.ofNullable(fixings.get(currency))
                .map(byDate -> byDate.floorEntry(date))
                .map(fixing -> new CnbRate(currency, fixing.getKey(), fixing.getValue()));
    }
}
