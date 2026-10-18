package org.example.budget.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Persistent Recurring Templates and the occurrences generated from them. */
public interface RecurringTemplateRepository {

    /** Stores a new template and returns it with its assigned id. */
    RecurringTemplate add(RecurringTemplateDetails details);

    Optional<RecurringTemplate> find(long id);

    /**
     * Replaces the details of an existing template and remembers the day of this edit; its generated
     * occurrences stay as they are.
     */
    void update(RecurringTemplate template, LocalDate editedOn);

    /** The day the template was last edited, if ever. */
    Optional<LocalDate> lastEdited(long templateId);

    /** All templates in the order they were added. */
    List<RecurringTemplate> findAll();

    /** Moves every template of one Category to another (the merge when a Category is deleted). */
    void moveToCategory(long fromCategoryId, long toCategoryId);

    /**
     * Records that the template's occurrence on this date was generated as the given Transaction. The record
     * stays when that Transaction is deleted, so the occurrence is never generated again.
     */
    void addOccurrence(long templateId, LocalDate occurrenceDate, long transactionId);

    /** The date of the template's latest generated occurrence, if any. */
    Optional<LocalDate> lastOccurrence(long templateId);

    /** The ids of all Transactions generated from any template (deleted ones may be left out). */
    Set<Long> generatedTransactionIds();
}
