package org.example.budget.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonthTest {

    @Test
    void isTheMonthOfADate() {
        assertEquals(Month.of(2026, 10), Month.of(LocalDate.of(2026, 10, 17)));
    }

    @Test
    void navigatesAcrossYearBoundaries() {
        assertEquals(Month.of(2025, 12), Month.of(2026, 1).previous());
        assertEquals(Month.of(2027, 1), Month.of(2026, 12).next());
        assertEquals(Month.of(2026, 9), Month.of(2026, 10).previous());
    }

    @Test
    void knowsItsNumberOfDays() {
        assertEquals(31, Month.of(2026, 10).days());
        assertEquals(30, Month.of(2026, 9).days());
        assertEquals(28, Month.of(2026, 2).days());
        assertEquals(29, Month.of(2028, 2).days());
    }

    @Test
    void containsExactlyItsOwnDays() {
        Month october = Month.of(2026, 10);
        assertTrue(october.contains(LocalDate.of(2026, 10, 1)));
        assertTrue(october.contains(LocalDate.of(2026, 10, 31)));
        assertFalse(october.contains(LocalDate.of(2026, 9, 30)));
        assertFalse(october.contains(LocalDate.of(2026, 11, 1)));
        assertFalse(october.contains(LocalDate.of(2025, 10, 15)));
    }

    @Test
    void knowsItsFirstAndLastDay() {
        assertEquals(LocalDate.of(2026, 2, 1), Month.of(2026, 2).firstDay());
        assertEquals(LocalDate.of(2026, 2, 28), Month.of(2026, 2).lastDay());
    }
}
