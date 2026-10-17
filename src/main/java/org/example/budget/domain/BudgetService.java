package org.example.budget.domain;

import org.example.budget.domain.InvalidTransactionException.Problem;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

/**
 * The Model's public API used by Controllers. The UI never reaches storage directly.
 */
public class BudgetService {

    private static final String OPENING_SAVINGS = "opening_savings";

    private final SettingsRepository settings;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final Clock clock;
    private final CnbRates cnbRates;

    public BudgetService(SettingsRepository settings, CategoryRepository categories,
                         TransactionRepository transactions, CnbRateRepository rateCache, RateSource rateSource,
                         Clock clock) {
        this.settings = settings;
        this.categories = categories;
        this.transactions = transactions;
        this.clock = clock;
        this.cnbRates = new CnbRates(rateCache, rateSource, clock);
    }

    /** A service without ČNB Rates (every rate is unavailable), for scenarios that only use CZK. */
    public BudgetService(SettingsRepository settings, CategoryRepository categories,
                         TransactionRepository transactions, Clock clock) {
        this(settings, categories, transactions, CnbRates.NO_CACHE, RateSource.UNAVAILABLE, clock);
    }

    public Month currentMonth() {
        return Month.of(clock.today());
    }

    public Money openingSavings() {
        return settings.find(OPENING_SAVINGS)
                .map(halere -> Money.ofHalere(Long.parseLong(halere)))
                .orElse(Money.ZERO);
    }

    public void setOpeningSavings(Money amount) {
        settings.save(OPENING_SAVINGS, Long.toString(amount.halere()));
    }

    // --- Settings and first launch ---

    private static final String LANGUAGE = "language";
    private static final String THEME = "theme";

    /** True until the first-launch dialog has been completed on this database. */
    public boolean isFirstLaunch() {
        return settings.find(LANGUAGE).isEmpty();
    }

    /** Remembers the language chosen on first launch and creates the preset Categories named in it. */
    public void completeFirstLaunch(Language language, PresetCategoryNames names) {
        seedPresetCategories(preset -> names.name(preset, language));
        setLanguage(language);
    }

    /** The UI language; English until one is chosen. */
    public Language language() {
        return settings.find(LANGUAGE).flatMap(Language::fromTag).orElse(Language.ENGLISH);
    }

    /** Changes the UI language. Existing Category names stay as they are. */
    public void setLanguage(Language language) {
        settings.save(LANGUAGE, language.tag());
    }

    /** The UI theme; light until one is chosen. */
    public Theme theme() {
        return settings.find(THEME).map(Theme::valueOf).orElse(Theme.LIGHT);
    }

    public void setTheme(Theme theme) {
        settings.save(THEME, theme.name());
    }

    // --- Categories ---

    /** On first start (no Categories yet), creates the preset Categories named in the chosen language. */
    public void seedPresetCategories(Function<PresetCategory, String> name) {
        if (!categories.findAll().isEmpty()) {
            return;
        }
        for (PresetCategory preset : PresetCategory.values()) {
            categories.add(name.apply(preset), preset.type(), preset.system());
        }
    }

    /** The Categories of one type, in the order they were created. */
    public List<Category> categories(TransactionType type) {
        return categories.findAll().stream().filter(category -> category.type() == type).toList();
    }

    /** Adds a user Category to the end of its type's list. */
    public Category addCategory(String name, TransactionType type) {
        return categories.add(validName(name, type, OptionalLong.empty()), type, false);
    }

    /** Renames a Category; its Transactions stay attached because they refer to it by id. */
    public void renameCategory(long id, String name) {
        Category category = existingCategory(id);
        categories.rename(id, validName(name, category.type(), OptionalLong.of(id)));
    }

    /** Whether any Transaction (in any Month) uses the Category, so deleting it needs a replacement. */
    public boolean hasTransactions(long categoryId) {
        return !transactions.find(TransactionFilter.ALL.inCategory(categoryId)).isEmpty();
    }

    /** Deletes a Category that has no Transactions. From Savings can't be deleted. */
    public void deleteCategory(long id) {
        deletable(id);
        if (hasTransactions(id)) {
            throw new InvalidCategoryException(InvalidCategoryException.Problem.REPLACEMENT_REQUIRED);
        }
        categories.delete(id);
    }

    /**
     * Deletes a Category and merges it into the replacement: all its Transactions move there.
     * The replacement must be another Category of the same type. From Savings can't be merged away.
     */
    public void deleteCategory(long id, long replacementId) {
        Category deleted = deletable(id);
        if (replacementId == id) {
            throw new InvalidCategoryException(InvalidCategoryException.Problem.REPLACEMENT_IS_SAME);
        }
        if (existingCategory(replacementId).type() != deleted.type()) {
            throw new InvalidCategoryException(InvalidCategoryException.Problem.REPLACEMENT_OF_OTHER_TYPE);
        }
        transactions.moveToCategory(id, replacementId);
        categories.delete(id);
    }

    private Category deletable(long id) {
        Category category = existingCategory(id);
        if (category.system()) {
            throw new InvalidCategoryException(InvalidCategoryException.Problem.SYSTEM_CATEGORY);
        }
        return category;
    }

    private Category existingCategory(long id) {
        return categories.find(id)
                .orElseThrow(() -> new InvalidCategoryException(InvalidCategoryException.Problem.UNKNOWN_CATEGORY));
    }

    /** The stripped name, if it's not blank and no other Category of the type has it (ignoring case). */
    private String validName(String name, TransactionType type, OptionalLong except) {
        String stripped = name == null ? "" : name.strip();
        if (stripped.isEmpty()) {
            throw new InvalidCategoryException(InvalidCategoryException.Problem.BLANK_NAME);
        }
        boolean taken = categories(type).stream()
                .filter(other -> except.isEmpty() || other.id() != except.getAsLong())
                .anyMatch(other -> fold(other.name()).equals(fold(stripped)));
        if (taken) {
            throw new InvalidCategoryException(InvalidCategoryException.Problem.DUPLICATE_NAME);
        }
        return stripped;
    }

    private static String fold(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    // --- Transactions ---

    /** The date a new Transaction starts with. */
    public LocalDate today() {
        return clock.today();
    }

    /** Records an Income or Expense in any Month. A negative Expense amount is a Refund. */
    public Transaction recordTransaction(TransactionDetails details) {
        validate(details);
        return transactions.add(details);
    }

    /** Replaces every field of a Transaction in any Month, past or present. */
    public void editTransaction(long id, TransactionDetails details) {
        if (transactions.find(id).isEmpty()) {
            throw new InvalidTransactionException(Problem.UNKNOWN_TRANSACTION);
        }
        validate(details);
        transactions.update(new Transaction(id, details));
    }

    public void deleteTransaction(long id) {
        transactions.delete(id);
    }

    /** The Transactions matching the filter, newest first. */
    public List<Transaction> transactions(TransactionFilter filter) {
        return transactions.find(filter);
    }

    /** The sum of a Category's Transactions in a Month; Refunds lower it. */
    public Money categoryTotal(long categoryId, Month month) {
        return transactions.find(TransactionFilter.ALL.inMonth(month).inCategory(categoryId)).stream()
                .map(Transaction::amount)
                .reduce(Money.ZERO, Money::plus);
    }

    // --- Foreign currency ---

    /** The ČNB Rate for a currency on a date, from the cache or else from ČNB. Empty when unavailable. */
    public Optional<CnbRate> cnbRate(Currency currency, LocalDate date) {
        return cnbRates.rate(currency, date);
    }

    /**
     * Converts an Original Amount with the ČNB Rate for the Transaction's date; an optional Charged Amount
     * overrides the result. Empty when the rate is unavailable.
     */
    public Optional<Conversion> convert(OriginalAmount amount, LocalDate date, Optional<Money> chargedAmount) {
        return cnbRate(amount.currency(), date).map(rate -> new Conversion(amount, rate, chargedAmount));
    }

    // --- Month dashboard ---

    /**
     * The Month's total Income, total Expenses, Leftover and Expenses per Category (largest first,
     * then by name). Refunds lower their Category's total and the total Expenses.
     */
    public MonthSummary monthSummary(Month month) {
        Money income = Money.ZERO;
        Money expenses = Money.ZERO;
        List<MonthSummary.CategorySpending> byCategory = new ArrayList<>();
        for (CategoryTotal total : transactions.categoryTotals(month)) {
            if (total.type() == TransactionType.INCOME) {
                income = income.plus(total.total());
            } else {
                expenses = expenses.plus(total.total());
                categories.find(total.categoryId()).ifPresent(category ->
                        byCategory.add(new MonthSummary.CategorySpending(category, total.total())));
            }
        }
        byCategory.sort(Comparator
                .comparingLong((MonthSummary.CategorySpending spending) -> spending.total().halere()).reversed()
                .thenComparing(spending -> spending.category().name()));
        return new MonthSummary(month, income, expenses, byCategory, isDeficit(month, income.minus(expenses)));
    }

    // --- Deficits ---

    /** Every Month with an Uncovered Deficit, oldest first, with its (negative) Leftover. */
    public List<Deficit> uncoveredDeficits() {
        return transactions.monthTotals().stream()
                .filter(totals -> isDeficit(totals.month(), totals.leftover()))
                .map(totals -> new Deficit(totals.month(), totals.leftover()))
                .toList();
    }

    /**
     * A Deficit is a completed Month whose Leftover is negative. It stays uncovered until Income added to
     * that Month brings the Leftover back to zero or above. The running Month is never a Deficit.
     */
    private boolean isDeficit(Month month, Money leftover) {
        return month.compareTo(currentMonth()) < 0 && leftover.halere() < 0;
    }

    private void validate(TransactionDetails details) {
        Category category = categories.find(details.categoryId())
                .orElseThrow(() -> new InvalidTransactionException(Problem.UNKNOWN_CATEGORY));
        if (category.type() != details.type()) {
            throw new InvalidTransactionException(Problem.CATEGORY_OF_OTHER_TYPE);
        }
        long amount = details.amount().halere();
        boolean zeroOriginalAmount = details.conversion().map(c -> c.originalAmount().cents() == 0).orElse(false);
        if (amount == 0 || zeroOriginalAmount) {
            throw new InvalidTransactionException(Problem.ZERO_AMOUNT);
        }
        if (amount < 0 && details.type() == TransactionType.INCOME) {
            throw new InvalidTransactionException(Problem.NEGATIVE_INCOME);
        }
    }
}
