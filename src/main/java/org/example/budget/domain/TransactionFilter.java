package org.example.budget.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Narrows the Transaction list. Every part is optional; an empty filter matches everything.
 * The Note search is a case-insensitive substring match.
 */
public record TransactionFilter(
        Optional<Month> month,
        Optional<Long> categoryId,
        Optional<TransactionType> type,
        String noteSearch) {

    public static final TransactionFilter ALL =
            new TransactionFilter(Optional.empty(), Optional.empty(), Optional.empty(), "");

    public TransactionFilter {
        Objects.requireNonNull(month);
        Objects.requireNonNull(categoryId);
        Objects.requireNonNull(type);
        noteSearch = noteSearch == null ? "" : noteSearch.strip();
    }

    public TransactionFilter inMonth(Month value) {
        return new TransactionFilter(Optional.of(value), categoryId, type, noteSearch);
    }

    public TransactionFilter inCategory(long value) {
        return new TransactionFilter(month, Optional.of(value), type, noteSearch);
    }

    public TransactionFilter ofType(TransactionType value) {
        return new TransactionFilter(month, categoryId, Optional.of(value), noteSearch);
    }

    public TransactionFilter withNoteContaining(String value) {
        return new TransactionFilter(month, categoryId, type, value);
    }

    public boolean matches(Transaction transaction) {
        return month.map(m -> m.contains(transaction.date())).orElse(true)
                && categoryId.map(id -> id == transaction.categoryId()).orElse(true)
                && type.map(t -> t == transaction.type()).orElse(true)
                && noteMatches(transaction.note());
    }

    /** Whether the Note contains the search text, ignoring case (also for Czech letters such as Č/č). */
    public boolean noteMatches(String note) {
        return noteSearch.isEmpty() || fold(note).contains(fold(noteSearch));
    }

    private static String fold(String text) {
        return text.toLowerCase(Locale.ROOT);
    }
}
