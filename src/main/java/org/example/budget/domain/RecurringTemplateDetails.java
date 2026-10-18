package org.example.budget.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the user enters for a Recurring Template: type, Category, amount, Note, frequency, day of month,
 * start date and optional end date. A yearly template repeats in its start date's month. A blank Note is
 * stored as no Note ({@code ""}).
 * <p>
 * A template in a foreign currency (e.g. a EUR Subscription) has an Original Amount instead of a CZK amount
 * ({@code amount} is then zero): each occurrence is converted at the ČNB Rate of its own due date.
 */
public record RecurringTemplateDetails(
        TransactionType type,
        long categoryId,
        Money amount,
        String note,
        Frequency frequency,
        int dayOfMonth,
        LocalDate startDate,
        Optional<LocalDate> endDate,
        Optional<OriginalAmount> originalAmount) {

    public RecurringTemplateDetails {
        Objects.requireNonNull(type);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(frequency);
        Objects.requireNonNull(startDate);
        Objects.requireNonNull(endDate);
        Objects.requireNonNull(originalAmount);
        note = note == null ? "" : note.strip();
        if (originalAmount.isPresent() && !amount.equals(Money.ZERO)) {
            throw new IllegalArgumentException("A foreign-currency template has no CZK amount: " + amount);
        }
    }

    /** A template in CZK. */
    public RecurringTemplateDetails(TransactionType type, long categoryId, Money amount, String note,
                                    Frequency frequency, int dayOfMonth, LocalDate startDate,
                                    Optional<LocalDate> endDate) {
        this(type, categoryId, amount, note, frequency, dayOfMonth, startDate, endDate, Optional.empty());
    }

    /** A template in a foreign currency, whose occurrences are converted at their due dates' ČNB Rates. */
    public static RecurringTemplateDetails inForeignCurrency(TransactionType type, long categoryId,
                                                             OriginalAmount originalAmount, String note,
                                                             Frequency frequency, int dayOfMonth,
                                                             LocalDate startDate, Optional<LocalDate> endDate) {
        return new RecurringTemplateDetails(type, categoryId, Money.ZERO, note, frequency, dayOfMonth, startDate,
                endDate, Optional.of(originalAmount));
    }

    /** The currency the amount is in: CZK, or the Original Amount's currency. */
    public Currency currency() {
        return originalAmount.map(OriginalAmount::currency).orElse(Currency.CZK);
    }

    /** The same template in another Category. */
    public RecurringTemplateDetails inCategory(long otherCategoryId) {
        return new RecurringTemplateDetails(type, otherCategoryId, amount, note, frequency, dayOfMonth, startDate,
                endDate, originalAmount);
    }

    /** The same template ending on this date. */
    public RecurringTemplateDetails endingOn(LocalDate endDate) {
        return new RecurringTemplateDetails(type, categoryId, amount, note, frequency, dayOfMonth, startDate,
                Optional.of(endDate), originalAmount);
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
