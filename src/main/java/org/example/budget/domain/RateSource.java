package org.example.budget.domain;

import java.time.LocalDate;
import java.util.Optional;

/** Where ČNB Rates come from (the ČNB service in the app, a fake in tests). */
@FunctionalInterface
public interface RateSource {

    /**
     * The ČNB Rate for a currency on a date. For weekends, holidays and dates without a fixing yet, that is
     * the latest earlier fixing, with its own {@link CnbRate#validFor()}. Empty when the rate can't be
     * obtained right now (e.g. offline).
     */
    Optional<CnbRate> fetch(Currency currency, LocalDate date);
}
