package org.example.budget.domain;

import java.time.LocalDate;

/** The domain's only source of "today", injected so tests can fix the date. */
@FunctionalInterface
public interface Clock {

    LocalDate today();

    static Clock system() {
        return LocalDate::now;
    }

    static Clock fixed(LocalDate today) {
        return () -> today;
    }
}
