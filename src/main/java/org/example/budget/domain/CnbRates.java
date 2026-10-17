package org.example.budget.domain;

import java.time.LocalDate;
import java.util.Optional;

/**
 * ČNB Rate lookup: the cache first, the Rate source only on a miss, so each rate is downloaded once.
 * <p>
 * A fetched rate is always cached for its own fixing date, which never changes. It is cached for the date
 * asked for only when that date is already past: for today (before ČNB publishes at about 14:30) or a future
 * date ČNB answers with an earlier fixing that will still be superseded.
 */
final class CnbRates {

    /** A cache that keeps nothing. */
    static final CnbRateRepository NO_CACHE = new CnbRateRepository() {
        @Override
        public Optional<CnbRate> find(Currency currency, LocalDate date) {
            return Optional.empty();
        }

        @Override
        public void save(LocalDate date, CnbRate rate) {
        }
    };

    private final CnbRateRepository cache;
    private final RateSource source;
    private final Clock clock;

    CnbRates(CnbRateRepository cache, RateSource source, Clock clock) {
        this.cache = cache;
        this.source = source;
        this.clock = clock;
    }

    Optional<CnbRate> rate(Currency currency, LocalDate date) {
        Optional<CnbRate> cached = cache.find(currency, date);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<CnbRate> fetched = source.fetch(currency, date);
        fetched.ifPresent(rate -> {
            cache.save(rate.validFor(), rate);
            if (date.isBefore(clock.today())) {
                cache.save(date, rate);
            }
        });
        return fetched;
    }
}
