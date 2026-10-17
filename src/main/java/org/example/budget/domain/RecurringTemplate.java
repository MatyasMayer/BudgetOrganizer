package org.example.budget.domain;

import java.util.Objects;

/** A definition of a Transaction that repeats monthly or yearly, from which occurrences are generated. */
public record RecurringTemplate(long id, RecurringTemplateDetails details) {

    public RecurringTemplate {
        Objects.requireNonNull(details);
    }
}
