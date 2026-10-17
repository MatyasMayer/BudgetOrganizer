package org.example.budget.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The shared suite every {@link BudgetLimitRepository} must pass (in-memory fake and SQLite).
 * Categories come from the matching {@link CategoryRepository}, since SQLite enforces the reference.
 */
public abstract class BudgetLimitRepositoryContract {

    private static final Month SEPTEMBER = Month.of(2026, 9);
    private static final Month OCTOBER = Month.of(2026, 10);
    private static final Month DECEMBER = Month.of(2026, 12);

    private BudgetLimitRepository repository;
    private long food;
    private long games;

    /** Fresh, empty repositories sharing one store. */
    protected abstract Repositories createRepositories() throws Exception;

    public record Repositories(CategoryRepository categories, BudgetLimitRepository limits) {
    }

    @BeforeEach
    void setUp() throws Exception {
        Repositories repositories = createRepositories();
        repository = repositories.limits();
        food = repositories.categories().add("Food", TransactionType.EXPENSE, false).id();
        games = repositories.categories().add("Games", TransactionType.EXPENSE, false).id();
    }

    @Test
    void aNewRepositoryHasNoBudgetLimits() {
        assertEquals(List.of(), repository.findByCategory(food));
        assertEquals(List.of(), repository.findFor(OCTOBER));
    }

    @Test
    void aCategorysDefaultLimitComesFirstThenItsLimitOverridesByMonth() {
        BudgetLimit december = BudgetLimit.override(food, DECEMBER, czk(9_000));
        BudgetLimit standard = BudgetLimit.defaultLimit(food, czk(6_000));
        BudgetLimit september = BudgetLimit.override(food, SEPTEMBER, czk(5_000));
        repository.save(december);
        repository.save(standard);
        repository.save(september);
        repository.save(BudgetLimit.defaultLimit(games, czk(1_500)));

        assertEquals(List.of(standard, september, december), repository.findByCategory(food));
    }

    @Test
    void savingAgainReplacesTheLimitForTheSameMonthOnly() {
        repository.save(BudgetLimit.defaultLimit(food, czk(6_000)));
        repository.save(BudgetLimit.override(food, DECEMBER, czk(9_000)));

        repository.save(BudgetLimit.defaultLimit(food, czk(6_500)));
        repository.save(BudgetLimit.override(food, DECEMBER, czk(10_000)));

        assertEquals(List.of(BudgetLimit.defaultLimit(food, czk(6_500)), BudgetLimit.override(food, DECEMBER, czk(10_000))),
                repository.findByCategory(food));
    }

    @Test
    void aMonthSeesEveryDefaultLimitAndOnlyItsOwnLimitOverrides() {
        BudgetLimit foodDefault = BudgetLimit.defaultLimit(food, czk(6_000));
        BudgetLimit foodOctober = BudgetLimit.override(food, OCTOBER, czk(7_000));
        BudgetLimit gamesOctober = BudgetLimit.override(games, OCTOBER, czk(500));
        repository.save(foodDefault);
        repository.save(foodOctober);
        repository.save(gamesOctober);
        repository.save(BudgetLimit.override(food, SEPTEMBER, czk(5_000)));
        repository.save(BudgetLimit.override(games, Month.of(2025, 10), czk(800)));

        assertEquals(Set.of(foodDefault, foodOctober, gamesOctober), Set.copyOf(repository.findFor(OCTOBER)));
        assertEquals(Set.of(foodDefault), Set.copyOf(repository.findFor(Month.of(2027, 1))));
    }

    @Test
    void deletingALimitOverrideKeepsTheDefaultLimitAndOtherMonths() {
        repository.save(BudgetLimit.defaultLimit(food, czk(6_000)));
        repository.save(BudgetLimit.override(food, OCTOBER, czk(7_000)));
        repository.save(BudgetLimit.override(food, DECEMBER, czk(9_000)));

        repository.delete(food, Optional.of(OCTOBER));

        assertEquals(List.of(BudgetLimit.defaultLimit(food, czk(6_000)), BudgetLimit.override(food, DECEMBER, czk(9_000))),
                repository.findByCategory(food));
    }

    @Test
    void deletingTheDefaultLimitKeepsLimitOverridesAndOtherCategories() {
        repository.save(BudgetLimit.defaultLimit(food, czk(6_000)));
        repository.save(BudgetLimit.override(food, DECEMBER, czk(9_000)));
        repository.save(BudgetLimit.defaultLimit(games, czk(1_500)));

        repository.delete(food, Optional.empty());

        assertEquals(List.of(BudgetLimit.override(food, DECEMBER, czk(9_000))), repository.findByCategory(food));
        assertEquals(List.of(BudgetLimit.defaultLimit(games, czk(1_500))), repository.findByCategory(games));
    }

    @Test
    void deletingALimitThatDoesNotExistChangesNothing() {
        repository.save(BudgetLimit.defaultLimit(food, czk(6_000)));

        repository.delete(food, Optional.of(OCTOBER));
        repository.delete(games, Optional.empty());

        assertEquals(List.of(BudgetLimit.defaultLimit(food, czk(6_000))), repository.findByCategory(food));
    }

    @Test
    void amountsKeepTheirHalere() {
        repository.save(BudgetLimit.defaultLimit(food, Money.ofHalere(612_345)));

        assertEquals(Money.ofHalere(612_345), repository.findByCategory(food).getFirst().amount());
    }

    private static Money czk(long czk) {
        return Money.ofHalere(czk * 100);
    }
}
