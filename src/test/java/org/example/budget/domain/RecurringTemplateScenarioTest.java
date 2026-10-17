package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Facade-level scenarios for Recurring Templates in CZK: creating them and generating their occurrences. */
class RecurringTemplateScenarioTest {

    private final AtomicReference<LocalDate> today = new AtomicReference<>(LocalDate.of(2026, 10, 17));

    private final BudgetService service = new BudgetService(
            new InMemorySettingsRepository(), new InMemoryCategoryRepository(), new InMemoryTransactionRepository(),
            new InMemoryRecurringTemplateRepository(), new InMemoryBudgetLimitRepository(), today::get);

    private long rent;
    private long salary;

    @BeforeEach
    void seed() {
        service.seedPresetCategories(PresetCategory::name);
        rent = category(TransactionType.EXPENSE, "RENT");
        salary = category(TransactionType.INCOME, "SALARY");
    }

    @Test
    void aCreatedTemplateIsListedWithEverythingItWasGiven() {
        RecurringTemplateDetails details = new RecurringTemplateDetails(TransactionType.EXPENSE, rent,
                Money.ofHalere(1_200_000), "Flat", Frequency.MONTHLY, 1,
                LocalDate.of(2026, 11, 1), Optional.of(LocalDate.of(2027, 6, 30)));

        RecurringTemplate created = service.createRecurringTemplate(details);

        assertEquals(details, created.details());
        assertEquals(List.of(created), service.recurringTemplates());
    }

    @Test
    void everyOccurrenceFromTheStartUpToTodayIsGeneratedAsATransaction() {
        service.createRecurringTemplate(monthly(TransactionType.EXPENSE, rent, 1_200_000, "Flat", 1,
                LocalDate.of(2026, 8, 1)));

        service.generateDueOccurrences();

        assertEquals(List.of(
                        new TransactionDetails(LocalDate.of(2026, 10, 1), TransactionType.EXPENSE, rent,
                                Money.ofHalere(1_200_000), "Flat"),
                        new TransactionDetails(LocalDate.of(2026, 9, 1), TransactionType.EXPENSE, rent,
                                Money.ofHalere(1_200_000), "Flat"),
                        new TransactionDetails(LocalDate.of(2026, 8, 1), TransactionType.EXPENSE, rent,
                                Money.ofHalere(1_200_000), "Flat")),
                details(service.transactions(TransactionFilter.ALL)));
    }

    @Test
    void aDayBeyondAMonthsLengthFallsOnItsLastDay() {
        service.createRecurringTemplate(monthly(TransactionType.EXPENSE, rent, 1_200_000, "Flat", 31,
                LocalDate.of(2026, 1, 31)));

        service.generateDueOccurrences();

        assertEquals(List.of(
                LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 31), LocalDate.of(2026, 6, 30),
                LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 30)),
                datesIn(rent));
    }

    @Test
    void inALeapYearTheThirtiethFallsOnFebruaryTheTwentyNinth() {
        today.set(LocalDate.of(2028, 3, 5));
        service.createRecurringTemplate(monthly(TransactionType.INCOME, salary, 3_000_000, "Salary", 30,
                LocalDate.of(2028, 1, 1)));

        service.generateDueOccurrences();

        assertEquals(List.of(LocalDate.of(2028, 1, 30), LocalDate.of(2028, 2, 29)), datesIn(salary));
    }

    @Test
    void aYearlyTemplateGeneratesOncePerYearOnItsMonthAndDay() {
        long subscriptions = category(TransactionType.EXPENSE, "SUBSCRIPTIONS");
        service.createRecurringTemplate(new RecurringTemplateDetails(TransactionType.EXPENSE, subscriptions,
                Money.ofHalere(99_900), "Domain renewal", Frequency.YEARLY, 15,
                LocalDate.of(2024, 3, 15), Optional.empty()));

        service.generateDueOccurrences();

        assertEquals(List.of(LocalDate.of(2024, 3, 15), LocalDate.of(2025, 3, 15), LocalDate.of(2026, 3, 15)),
                datesIn(subscriptions));
    }

    @Test
    void aYearlyTemplateOnTheTwentyNinthOfFebruaryFallsOnTheTwentyEighthInOtherYears() {
        service.createRecurringTemplate(new RecurringTemplateDetails(TransactionType.INCOME, salary,
                Money.ofHalere(500_000), "Birthday bonus", Frequency.YEARLY, 29,
                LocalDate.of(2024, 2, 1), Optional.empty()));

        service.generateDueOccurrences();

        assertEquals(List.of(LocalDate.of(2024, 2, 29), LocalDate.of(2025, 2, 28), LocalDate.of(2026, 2, 28)),
                datesIn(salary));
    }

    @Test
    void noOccurrencesAreGeneratedAfterTheEndDate() {
        service.createRecurringTemplate(new RecurringTemplateDetails(TransactionType.EXPENSE, rent,
                Money.ofHalere(1_200_000), "Old flat", Frequency.MONTHLY, 1,
                LocalDate.of(2026, 6, 1), Optional.of(LocalDate.of(2026, 8, 15))));

        service.generateDueOccurrences();

        assertEquals(List.of(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1)),
                datesIn(rent));
    }

    @Test
    void anOccurrenceOnTheEndDateIsStillGenerated() {
        service.createRecurringTemplate(new RecurringTemplateDetails(TransactionType.EXPENSE, rent,
                Money.ofHalere(1_200_000), "Old flat", Frequency.MONTHLY, 1,
                LocalDate.of(2026, 9, 1), Optional.of(LocalDate.of(2026, 10, 1))));

        service.generateDueOccurrences();

        assertEquals(List.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)), datesIn(rent));
    }

    @Test
    void generatingAgainCreatesNothingNew() {
        service.createRecurringTemplate(monthly(TransactionType.EXPENSE, rent, 1_200_000, "Flat", 1,
                LocalDate.of(2026, 9, 1)));

        service.generateDueOccurrences();
        service.generateDueOccurrences();

        assertEquals(List.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)), datesIn(rent));
    }

    @Test
    void aDeletedOccurrenceIsNeverGeneratedAgain() {
        service.createRecurringTemplate(monthly(TransactionType.EXPENSE, rent, 1_200_000, "Flat", 1,
                LocalDate.of(2026, 9, 1)));
        service.generateDueOccurrences();
        Transaction september = service.transactions(TransactionFilter.ALL.inMonth(Month.of(2026, 9))).getFirst();

        service.deleteTransaction(september.id());
        service.generateDueOccurrences();
        today.set(LocalDate.of(2026, 11, 1));
        service.generateDueOccurrences();

        assertEquals(List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)), datesIn(rent));
    }

    @Test
    void afterWeeksClosedEveryMissedOccurrenceIsCaughtUp() {
        service.createRecurringTemplate(monthly(TransactionType.EXPENSE, rent, 1_200_000, "Flat", 1,
                LocalDate.of(2026, 10, 1)));
        service.createRecurringTemplate(monthly(TransactionType.INCOME, salary, 3_500_000, "Employer", 15,
                LocalDate.of(2026, 10, 1)));
        service.generateDueOccurrences();

        today.set(LocalDate.of(2026, 12, 20));
        service.generateDueOccurrences();

        assertEquals(List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1)),
                datesIn(rent));
        assertEquals(List.of(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 15), LocalDate.of(2026, 12, 15)),
                datesIn(salary));
    }

    @Test
    void anOccurrenceIsGeneratedOnTheDayItComesDueAndNotBefore() {
        service.createRecurringTemplate(monthly(TransactionType.INCOME, salary, 3_500_000, "Employer", 18,
                LocalDate.of(2026, 10, 1)));
        service.generateDueOccurrences();
        assertEquals(List.of(), datesIn(salary));

        today.set(LocalDate.of(2026, 10, 18));
        service.generateDueOccurrences();

        assertEquals(List.of(LocalDate.of(2026, 10, 18)), datesIn(salary));
    }

    @Test
    void aTemplateStartingInTheFutureGeneratesNothingYet() {
        service.createRecurringTemplate(monthly(TransactionType.EXPENSE, rent, 1_200_000, "New flat", 1,
                LocalDate.of(2026, 11, 1)));

        service.generateDueOccurrences();

        assertEquals(List.of(), datesIn(rent));
    }

    @Test
    void generatedTransactionsAreMarkedAndRecordedOnesAreNot() {
        service.createRecurringTemplate(monthly(TransactionType.EXPENSE, rent, 1_200_000, "Flat", 1,
                LocalDate.of(2026, 9, 1)));
        Transaction recorded = service.recordTransaction(new TransactionDetails(LocalDate.of(2026, 10, 5),
                TransactionType.EXPENSE, rent, Money.ofHalere(50_000), "Plumber"));

        service.generateDueOccurrences();

        Set<Long> generated = service.generatedTransactionIds();
        List<Transaction> list = service.transactions(TransactionFilter.ALL.inCategory(rent));
        assertEquals(List.of("Plumber", "Flat", "Flat"), list.stream().map(Transaction::note).toList());
        assertEquals(List.of(false, true, true), list.stream().map(t -> generated.contains(t.id())).toList());
        assertFalse(generated.contains(recorded.id()));
    }

    @Test
    void deletingACategoryMovesItsTemplatesToTheReplacement() {
        long subscriptions = category(TransactionType.EXPENSE, "SUBSCRIPTIONS");
        long other = category(TransactionType.EXPENSE, "OTHER_EXPENSE");
        RecurringTemplate spotify = service.createRecurringTemplate(monthly(TransactionType.EXPENSE, subscriptions,
                16_900, "Spotify", 5, LocalDate.of(2026, 9, 1)));
        service.generateDueOccurrences();

        service.deleteCategory(subscriptions, other);
        today.set(LocalDate.of(2026, 11, 5));
        service.generateDueOccurrences();

        assertEquals(other, service.recurringTemplates().getFirst().details().categoryId());
        assertEquals(spotify.id(), service.recurringTemplates().getFirst().id());
        assertEquals(List.of(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 5)),
                datesIn(other));
    }

    @Test
    void aCategoryUsedOnlyByATemplateStillNeedsAReplacement() {
        long subscriptions = category(TransactionType.EXPENSE, "SUBSCRIPTIONS");
        service.createRecurringTemplate(monthly(TransactionType.EXPENSE, subscriptions, 16_900, "Spotify", 5,
                LocalDate.of(2026, 11, 1)));

        assertTrue(service.needsReplacement(subscriptions));
        assertFalse(service.needsReplacement(category(TransactionType.EXPENSE, "HEALTH")));
        InvalidCategoryException error = assertThrows(InvalidCategoryException.class,
                () -> service.deleteCategory(subscriptions));
        assertEquals(InvalidCategoryException.Problem.REPLACEMENT_REQUIRED, error.problem());
    }

    @Test
    void aTemplateIsRefusedWhenItsInputDoesNotMakeSense() {
        LocalDate start = LocalDate.of(2026, 11, 1);
        assertRefused(InvalidRecurringTemplateException.Problem.UNKNOWN_CATEGORY,
                monthly(TransactionType.EXPENSE, 9_999, 100, "", 1, start));
        assertRefused(InvalidRecurringTemplateException.Problem.CATEGORY_OF_OTHER_TYPE,
                monthly(TransactionType.EXPENSE, salary, 100, "", 1, start));
        assertRefused(InvalidRecurringTemplateException.Problem.AMOUNT_NOT_POSITIVE,
                monthly(TransactionType.EXPENSE, rent, 0, "", 1, start));
        assertRefused(InvalidRecurringTemplateException.Problem.AMOUNT_NOT_POSITIVE,
                monthly(TransactionType.EXPENSE, rent, -100, "", 1, start));
        assertRefused(InvalidRecurringTemplateException.Problem.DAY_OUT_OF_RANGE,
                monthly(TransactionType.EXPENSE, rent, 100, "", 0, start));
        assertRefused(InvalidRecurringTemplateException.Problem.DAY_OUT_OF_RANGE,
                monthly(TransactionType.EXPENSE, rent, 100, "", 32, start));
        assertRefused(InvalidRecurringTemplateException.Problem.END_BEFORE_START,
                new RecurringTemplateDetails(TransactionType.EXPENSE, rent, Money.ofHalere(100), "",
                        Frequency.MONTHLY, 1, start, Optional.of(start.minusDays(1))));

        assertEquals(List.of(), service.recurringTemplates());
    }

    private void assertRefused(InvalidRecurringTemplateException.Problem problem, RecurringTemplateDetails details) {
        InvalidRecurringTemplateException error = assertThrows(InvalidRecurringTemplateException.class,
                () -> service.createRecurringTemplate(details));
        assertEquals(problem, error.problem());
    }

    private static RecurringTemplateDetails monthly(TransactionType type, long categoryId, long halere, String note,
                                                    int dayOfMonth, LocalDate start) {
        return new RecurringTemplateDetails(type, categoryId, Money.ofHalere(halere), note, Frequency.MONTHLY,
                dayOfMonth, start, Optional.empty());
    }

    private static List<TransactionDetails> details(List<Transaction> transactions) {
        return transactions.stream().map(Transaction::details).toList();
    }

    private List<LocalDate> datesIn(long categoryId) {
        return service.transactions(TransactionFilter.ALL.inCategory(categoryId)).stream()
                .map(Transaction::date).toList().reversed();
    }

    private long category(TransactionType type, String name) {
        return service.categories(type).stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow().id();
    }
}
