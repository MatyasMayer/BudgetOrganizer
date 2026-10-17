package org.example.budget.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistent Recurring Templates and the occurrences generated from them. */
public interface RecurringTemplateRepository {

    /** Stores a new template and returns it with its assigned id. */
    RecurringTemplate add(RecurringTemplateDetails details);

    Optional<RecurringTemplate> find(long id);

    /** All templates in the order they were added. */
    List<RecurringTemplate> findAll();

    /**
     * Records that the template's occurrence on this date was generated as the given Transaction. The record
     * stays when that Transaction is deleted, so the occurrence is never generated again.
     */
    void addOccurrence(long templateId, LocalDate occurrenceDate, long transactionId);

    /** The date of the template's latest generated occurrence, if any. */
    Optional<LocalDate> lastOccurrence(long templateId);
}
