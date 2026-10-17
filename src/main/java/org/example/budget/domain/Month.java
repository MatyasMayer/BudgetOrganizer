package org.example.budget.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * A calendar month, from its 1st to its last day. The unit for Budget Limits, Leftover and comparisons.
 */
public record Month(YearMonth yearMonth) implements Comparable<Month> {

    public Month {
        Objects.requireNonNull(yearMonth);
    }

    public static Month of(int year, int month) {
        return new Month(YearMonth.of(year, month));
    }

    public static Month of(LocalDate date) {
        return new Month(YearMonth.from(date));
    }

    public int year() {
        return yearMonth.getYear();
    }

    public int monthValue() {
        return yearMonth.getMonthValue();
    }

    public Month previous() {
        return new Month(yearMonth.minusMonths(1));
    }

    public Month next() {
        return new Month(yearMonth.plusMonths(1));
    }

    public int days() {
        return yearMonth.lengthOfMonth();
    }

    public LocalDate firstDay() {
        return yearMonth.atDay(1);
    }

    public LocalDate lastDay() {
        return yearMonth.atEndOfMonth();
    }

    public boolean contains(LocalDate date) {
        return YearMonth.from(date).equals(yearMonth);
    }

    @Override
    public int compareTo(Month other) {
        return yearMonth.compareTo(other.yearMonth);
    }

    @Override
    public String toString() {
        return yearMonth.toString();
    }
}
