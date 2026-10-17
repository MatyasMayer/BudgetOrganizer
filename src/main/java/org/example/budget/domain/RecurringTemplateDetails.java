package org.example.budget.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the user enters for a Recurring Template in CZK: type, Category, amount, Note, frequency, day of month,
 * start date and optional end date. A yearly template repeats in its start date's month. A blank Note is
 * stored as no Note ({@code ""}).
 */
public record RecurringTemplateDetails(
        TransactionType type,
        long categoryId,
        Money amount,
        String note,
        Frequency frequency,
        int dayOfMonth,
        LocalDate startDate,
        Optional<LocalDate> endDate) {

    public RecurringTemplateDetails {
        Objects.requireNonNull(type);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(frequency);
        Objects.requireNonNull(startDate);
        Objects.requireNonNull(endDate);
        note = note == null ? "" : note.strip();
    }

    public boolean hasNote() {
        return !note.isEmpty();
    }

    /** Whether the template's end date lies before this date, so it generates nothing more. */
    public boolean hasEndedBy(LocalDate date) {
        return endDate.filter(end -> end.isBefore(date)).isPresent();
    }

    /**
     * The occurrence dates from the start date up to and including {@code until} (or the end date, if earlier),
     * oldest first. A day of month beyond a Month's length falls on its last day.
     */
    public List<LocalDate> occurrencesThrough(LocalDate until) {
        LocalDate through = endDate.filter(end -> end.isBefore(until)).orElse(until);
        int step = frequency == Frequency.YEARLY ? 12 : 1;
        List<LocalDate> dates = new ArrayList<>();
        for (YearMonth month = YearMonth.from(startDate); !month.atDay(1).isAfter(through);
             month = month.plusMonths(step)) {
            LocalDate date = month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
            if (!date.isBefore(startDate) && !date.isAfter(through)) {
                dates.add(date);
            }
        }
        return dates;
    }
}
