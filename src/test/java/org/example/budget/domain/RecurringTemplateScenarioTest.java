package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Facade-level scenarios for Recurring Templates in CZK: creating them and generating their occurrences. */
class RecurringTemplateScenarioTest {

    private final AtomicReference<LocalDate> today = new AtomicReference<>(LocalDate.of(2026, 10, 17));

    private final BudgetService service = new BudgetService(
            new InMemorySettingsRepository(), new InMemoryCategoryRepository(), new InMemoryTransactionRepository(),
            new InMemoryRecurringTemplateRepository(), today::get);

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
