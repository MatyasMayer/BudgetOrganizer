package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Facade-level scenarios for the Subscription overview: what the active Expense Recurring Templates cost. */
class SubscriptionOverviewScenarioTest {

    private final AtomicReference<LocalDate> today = new AtomicReference<>(LocalDate.of(2026, 10, 17));
    private final FakeRateSource cnb = new FakeRateSource()
            .publish(Currency.EUR, LocalDate.of(2026, 10, 16), "24.325");
    private final BudgetService service = new BudgetService(
            new InMemorySettingsRepository(), new InMemoryCategoryRepository(), new InMemoryTransactionRepository(),
            new InMemoryRecurringTemplateRepository(), new InMemoryBudgetLimitRepository(),
            new InMemoryCnbRateRepository(), cnb, today::get);

    private long subscriptions;

    @BeforeEach
    void seed() {
        service.seedPresetCategories(PresetCategory::name);
        subscriptions = category(TransactionType.EXPENSE, "SUBSCRIPTIONS");
    }

    @Test
    void aMonthlySubscriptionCostsItsAmountPerMonthAndTwelveTimesThatPerYear() {
        RecurringTemplate netflix = service.createRecurringTemplate(czk("Netflix", 29_900, Frequency.MONTHLY));

        SubscriptionOverview overview = service.subscriptionOverview();

        assertEquals(List.of(new Subscription(netflix, Money.ofHalere(29_900), Money.ofHalere(358_800))),
                overview.subscriptions());
        assertEquals(Money.ofHalere(29_900), overview.monthlyTotal());
        assertEquals(Money.ofHalere(358_800), overview.yearlyTotal());
    }

    @Test
    void aYearlySubscriptionCountsATwelfthPerMonthRoundedToTheHaler() {
        RecurringTemplate domain = service.createRecurringTemplate(czk("Domain", 100_000, Frequency.YEARLY));

        SubscriptionOverview overview = service.subscriptionOverview();

        // 1,000.00 / 12 = 83.333… → 83.33
        assertEquals(List.of(new Subscription(domain, Money.ofHalere(8_333), Money.ofHalere(100_000))),
                overview.subscriptions());
    }

    @Test
    void endedTemplatesAndIncomeTemplatesAreLeftOut() {
        RecurringTemplate endingToday = service.createRecurringTemplate(
                ended(czk("Spotify", 16_900, Frequency.MONTHLY), today.get()));
        service.createRecurringTemplate(ended(czk("Old gym", 79_000, Frequency.MONTHLY), LocalDate.of(2026, 10, 16)));
        service.createRecurringTemplate(new RecurringTemplateDetails(TransactionType.INCOME,
                category(TransactionType.INCOME, "SALARY"), Money.ofHalere(4_000_000), "Salary",
                Frequency.MONTHLY, 10, LocalDate.of(2026, 1, 10), Optional.empty()));

        SubscriptionOverview overview = service.subscriptionOverview();

        assertEquals(List.of(endingToday), overview.subscriptions().stream().map(Subscription::template).toList());
        assertEquals(Money.ofHalere(16_900), overview.monthlyTotal());
    }

    @Test
    void aEurSubscriptionIsConvertedAtTheLatestKnownCnbRate() {
        RecurringTemplate spotify = service.createRecurringTemplate(eur("Spotify", 1_099, Frequency.MONTHLY));
        RecurringTemplate icloud = service.createRecurringTemplate(eur("Cloud", 12_000, Frequency.YEARLY));

        SubscriptionOverview overview = service.subscriptionOverview();

        // 10.99 EUR × 24.325 = 267.33 per month; 120.00 EUR × 24.325 = 2,919.00 per year = 243.25 per month
        assertEquals(List.of(
                        new Subscription(spotify, Money.ofHalere(26_733), Money.ofHalere(320_796)),
                        new Subscription(icloud, Money.ofHalere(24_325), Money.ofHalere(291_900))),
                overview.subscriptions());
        assertEquals(Money.ofHalere(51_058), overview.monthlyTotal());
        assertEquals(Money.ofHalere(612_696), overview.yearlyTotal());
    }

    @Test
    void aEurSubscriptionStillCountsWhenCnbCannotBeReachedUsingTheMostRecentCachedRate() {
        service.cnbRate(Currency.EUR, LocalDate.of(2026, 10, 16));
        cnb.goOffline();
        today.set(LocalDate.of(2026, 10, 20));
        RecurringTemplate spotify = service.createRecurringTemplate(eur("Spotify", 1_099, Frequency.MONTHLY));

        assertEquals(List.of(spotify), service.subscriptionOverview().subscriptions().stream()
                .map(Subscription::template).toList());
    }

    @Test
    void aEurSubscriptionWithNoKnownRateIsReportedAsUnpricedInsteadOfCountedAsFree() {
        BudgetService offline = new BudgetService(
                new InMemorySettingsRepository(), new InMemoryCategoryRepository(), new InMemoryTransactionRepository(),
                new InMemoryRecurringTemplateRepository(), new InMemoryBudgetLimitRepository(), today::get);
        offline.seedPresetCategories(PresetCategory::name);
        long category = offline.categories(TransactionType.EXPENSE).stream()
                .filter(c -> c.name().equals("SUBSCRIPTIONS")).findFirst().orElseThrow().id();
        RecurringTemplate spotify = offline.createRecurringTemplate(RecurringTemplateDetails.inForeignCurrency(
                TransactionType.EXPENSE, category, new OriginalAmount(Currency.EUR, 1_099), "Spotify",
                Frequency.MONTHLY, 5, LocalDate.of(2026, 1, 5), Optional.empty()));

        SubscriptionOverview overview = offline.subscriptionOverview();

        assertEquals(List.of(), overview.subscriptions());
        assertEquals(List.of(spotify), overview.unpriced());
        assertEquals(Money.ZERO, overview.monthlyTotal());
    }

    private RecurringTemplateDetails eur(String note, long cents, Frequency frequency) {
        return RecurringTemplateDetails.inForeignCurrency(TransactionType.EXPENSE, subscriptions,
                new OriginalAmount(Currency.EUR, cents), note, frequency, 5, LocalDate.of(2026, 1, 5),
                Optional.empty());
    }

    private static RecurringTemplateDetails ended(RecurringTemplateDetails details, LocalDate endDate) {
        return new RecurringTemplateDetails(details.type(), details.categoryId(), details.amount(), details.note(),
                details.frequency(), details.dayOfMonth(), details.startDate(), Optional.of(endDate),
                details.originalAmount());
    }

    private RecurringTemplateDetails czk(String note, long halere, Frequency frequency) {
        return new RecurringTemplateDetails(TransactionType.EXPENSE, subscriptions, Money.ofHalere(halere), note,
                frequency, 5, LocalDate.of(2026, 1, 5), Optional.empty());
    }

    private long category(TransactionType type, String name) {
        return service.categories(type).stream()
                .filter(c -> c.name().equals(name)).findFirst().orElseThrow().id();
    }
}
