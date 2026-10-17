package org.example.budget.domain;

import org.example.budget.domain.InvalidTransactionException.Problem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;

/**
 * The Model's public API used by Controllers. The UI never reaches storage directly.
 */
public class BudgetService {

    private static final String OPENING_SAVINGS = "opening_savings";

    private final SettingsRepository settings;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final RecurringTemplateRepository recurringTemplates;
    private final BudgetLimitRepository limits;
    private final Clock clock;
    private final CnbRates cnbRates;

    public BudgetService(SettingsRepository settings, CategoryRepository categories,
                         TransactionRepository transactions, RecurringTemplateRepository recurringTemplates,
                         BudgetLimitRepository limits,
                         CnbRateRepository rateCache, RateSource rateSource, Clock clock) {
        this.settings = settings;
        this.categories = categories;
        this.transactions = transactions;
        this.recurringTemplates = recurringTemplates;
        this.limits = limits;
        this.clock = clock;
        this.cnbRates = new CnbRates(rateCache, rateSource, clock);
    }

    /** A service without ČNB Rates (every rate is unavailable), for scenarios that only use CZK. */
    public BudgetService(SettingsRepository settings, CategoryRepository categories,
                         TransactionRepository transactions, RecurringTemplateRepository recurringTemplates,
                         BudgetLimitRepository limits, Clock clock) {
        this(settings, categories, transactions, recurringTemplates, limits, CnbRates.NO_CACHE,
                RateSource.UNAVAILABLE, clock);
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

    /** Whether Transactions or Recurring Templates use the Category, so deleting it needs a replacement. */
    public boolean needsReplacement(long categoryId) {
        return hasTransactions(categoryId) || recurringTemplates.findAll().stream()
                .anyMatch(template -> template.details().categoryId() == categoryId);
    }

    /** Deletes a Category that no Transaction or Recurring Template uses. From Savings can't be deleted. */
    public void deleteCategory(long id) {
        deletable(id);
        if (needsReplacement(id)) {
            throw new InvalidCategoryException(InvalidCategoryException.Problem.REPLACEMENT_REQUIRED);
        }
        deleteBudgetLimits(id);
        categories.delete(id);
    }

    /**
     * Deletes a Category and merges it into the replacement: all its Transactions and Recurring Templates
     * move there.
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
        recurringTemplates.moveToCategory(id, replacementId);
        moveBudgetLimits(id, replacementId);
        categories.delete(id);
    }

    /** The replacement takes over the deleted Category's Budget Limits, keeping its own where it has one. */
    private void moveBudgetLimits(long fromCategoryId, long toCategoryId) {
        List<Optional<Month>> taken = limits.findByCategory(toCategoryId).stream().map(BudgetLimit::month).toList();
        for (BudgetLimit limit : limits.findByCategory(fromCategoryId)) {
            if (!taken.contains(limit.month())) {
                limits.save(new BudgetLimit(toCategoryId, limit.month(), limit.amount()));
            }
        }
        deleteBudgetLimits(fromCategoryId);
    }

    private void deleteBudgetLimits(long categoryId) {
        limits.findByCategory(categoryId).forEach(limit -> limits.delete(categoryId, limit.month()));
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

    // --- Budget Limits ---

    /** Sets the Budget Limit an Expense Category has in every Month that has no Limit Override. */
    public void setDefaultLimit(long categoryId, Money amount) {
        validateLimit(categoryId, amount);
        limits.save(BudgetLimit.defaultLimit(categoryId, amount));
    }

    public void clearDefaultLimit(long categoryId) {
        limits.delete(categoryId, Optional.empty());
    }

    /** Sets the Budget Limit an Expense Category has in one Month instead of its Default Limit. */
    public void setLimitOverride(long categoryId, Month month, Money amount) {
        validateLimit(categoryId, amount);
        limits.save(BudgetLimit.override(categoryId, month, amount));
    }

    /** Removes the Month's Limit Override, so the Category falls back to its Default Limit there. */
    public void clearLimitOverride(long categoryId, Month month) {
        limits.delete(categoryId, Optional.of(month));
    }

    public Optional<Money> defaultLimit(long categoryId) {
        return limit(categoryId, Optional.empty());
    }

    public Optional<Money> limitOverride(long categoryId, Month month) {
        return limit(categoryId, Optional.of(month));
    }

    /** The Month's Limit Override if there is one, otherwise the Default Limit, otherwise no limit. */
    public Optional<Money> effectiveLimit(long categoryId, Month month) {
        return limitOverride(categoryId, month).or(() -> defaultLimit(categoryId));
    }

    /** Each limited Category's effective limit in the Month: its Limit Override, else its Default Limit. */
    private Map<Long, Money> effectiveLimits(Month month) {
        Map<Long, Money> effective = new HashMap<>();
        for (BudgetLimit limit : limits.findFor(month)) {
            if (limit.isDefault()) {
                effective.putIfAbsent(limit.categoryId(), limit.amount());
            } else {
                effective.put(limit.categoryId(), limit.amount());
            }
        }
        return effective;
    }

    private void validateLimit(long categoryId, Money amount) {
        Category category = categories.find(categoryId).orElseThrow(
                () -> new InvalidBudgetLimitException(InvalidBudgetLimitException.Problem.UNKNOWN_CATEGORY));
        if (category.type() != TransactionType.EXPENSE) {
            throw new InvalidBudgetLimitException(InvalidBudgetLimitException.Problem.INCOME_CATEGORY);
        }
        if (amount.halere() <= 0) {
            throw new InvalidBudgetLimitException(InvalidBudgetLimitException.Problem.NOT_POSITIVE);
        }
    }

    private Optional<Money> limit(long categoryId, Optional<Month> month) {
        return limits.findByCategory(categoryId).stream()
                .filter(limit -> limit.month().equals(month))
                .map(BudgetLimit::amount)
                .findFirst();
    }

    // --- Foreign currency ---

    /** The ČNB Rate for a currency on a date, from the cache or else from ČNB. Empty when unavailable. */
    public Optional<CnbRate> cnbRate(Currency currency, LocalDate date) {
        return cnbRates.rate(currency, date);
    }

    /**
     * The rate a Transaction on the date can use: the ČNB Rate, or when ČNB can't be reached the most recent
     * cached rate as a Provisional Rate. Empty when not even a cached rate exists.
     */
    public Optional<AvailableRate> rateToUse(Currency currency, LocalDate date) {
        return cnbRates.rateToUse(currency, date);
    }

    /**
     * Converts an Original Amount with the ČNB Rate for the Transaction's date; an optional Charged Amount
     * overrides the result. When ČNB can't be reached, the most recent cached rate is used as a Provisional
     * Rate. With no rate at all, a Charged Amount is still enough (its implied rate is the Provisional Rate).
     * Empty when there is neither a rate nor a Charged Amount: the user has to enter one of them.
     */
    public Optional<Conversion> convert(OriginalAmount amount, LocalDate date, Optional<Money> chargedAmount) {
        return rateToUse(amount.currency(), date)
                .map(rate -> rate.convert(amount, chargedAmount))
                .or(() -> chargedAmount.flatMap(charged -> impliedRate(amount, charged, date))
                        .map(rate -> Conversion.provisional(amount, rate, chargedAmount)));
    }

    /** Converts at a rate the user entered (CZK per unit) because no ČNB Rate was available: a Provisional Rate. */
    public Conversion convertAtRate(OriginalAmount amount, LocalDate date, BigDecimal czkPerUnit,
                                    Optional<Money> chargedAmount) {
        return Conversion.provisional(amount, new CnbRate(amount.currency(), date, czkPerUnit), chargedAmount);
    }

    /**
     * Replaces every Provisional Rate whose ČNB Rate can now be fetched and recalculates the CZK amount.
     * Transactions with a Charged Amount are left untouched. Stops at the first rate still unavailable, since
     * ČNB is then most likely unreachable. Returns how many Transactions were updated.
     */
    public int refreshProvisionalRates() {
        int refreshed = 0;
        for (Transaction transaction : transactions.find(TransactionFilter.ALL)) {
            Optional<Conversion> conversion = transaction.details().conversion();
            if (conversion.isEmpty() || !conversion.get().provisionalRate()
                    || conversion.get().chargedAmount().isPresent()) {
                continue;
            }
            Conversion old = conversion.get();
            Optional<CnbRate> rate = cnbRate(old.originalAmount().currency(), transaction.date());
            if (rate.isEmpty()) {
                break;
            }
            TransactionDetails d = transaction.details();
            transactions.update(new Transaction(transaction.id(), TransactionDetails.converted(d.date(), d.type(),
                    d.categoryId(), new Conversion(old.originalAmount(), rate.get(), Optional.empty()), d.note())));
            refreshed++;
        }
        return refreshed;
    }

    private static Optional<CnbRate> impliedRate(OriginalAmount amount, Money charged, LocalDate date) {
        if (amount.cents() == 0) {
            return Optional.empty();
        }
        BigDecimal czkPerUnit = BigDecimal.valueOf(charged.halere())
                .divide(BigDecimal.valueOf(amount.cents()), 6, RoundingMode.HALF_UP);
        return czkPerUnit.signum() > 0
                ? Optional.of(new CnbRate(amount.currency(), date, czkPerUnit))
                : Optional.empty();
    }

    // --- Month dashboard ---

    /**
     * The Month's total Income, total Expenses, Leftover and Expenses per Category (largest first,
     * then by name). Refunds lower their Category's total and the total Expenses. Each Category carries
     * its effective Budget Limit, and a limited Category is listed even when nothing was spent in it yet.
     */
    public MonthSummary monthSummary(Month month) {
        Map<Long, Money> effectiveLimits = effectiveLimits(month);
        Money income = Money.ZERO;
        Money expenses = Money.ZERO;
        Map<Long, Money> spent = new LinkedHashMap<>();
        for (CategoryTotal total : transactions.categoryTotals(month)) {
            if (total.type() == TransactionType.INCOME) {
                income = income.plus(total.total());
            } else {
                expenses = expenses.plus(total.total());
                spent.put(total.categoryId(), total.total());
            }
        }
        effectiveLimits.keySet().forEach(categoryId -> spent.putIfAbsent(categoryId, Money.ZERO));
        List<MonthSummary.CategorySpending> byCategory = new ArrayList<>();
        spent.forEach((categoryId, total) -> categories.find(categoryId).ifPresent(category ->
                byCategory.add(new MonthSummary.CategorySpending(
                        category, total, Optional.ofNullable(effectiveLimits.get(categoryId))))));
        byCategory.sort(Comparator
                .comparingLong((MonthSummary.CategorySpending spending) -> spending.total().halere()).reversed()
                .thenComparing(spending -> spending.category().name()));
        return new MonthSummary(month, income, expenses, byCategory, isDeficit(month, income.minus(expenses)));
    }

    // --- Month comparison ---

    /**
     * Compares a Month's Expenses per Category with the previous Month. For the running Month, the Same-Point
     * Comparison covers the 1st through today in both Months, with today's day clamped to the previous Month's
     * length; the full-Month comparison is always included. Refunds lower their Category; Incomes are left out.
     */
    public MonthComparison monthComparison(Month month) {
        Month previous = month.previous();
        Optional<Comparison> samePoint = Optional.empty();
        if (month.equals(currentMonth())) {
            int day = Math.min(clock.today().getDayOfMonth(), previous.days());
            samePoint = Optional.of(compare(month.firstDay(), clock.today(),
                    previous.firstDay(), previous.firstDay().plusDays(day - 1L)));
        }
        Comparison fullMonth = compare(month.firstDay(), month.lastDay(), previous.firstDay(), previous.lastDay());
        return new MonthComparison(month, samePoint, fullMonth);
    }

    private Comparison compare(LocalDate from, LocalDate through, LocalDate previousFrom, LocalDate previousThrough) {
        Map<Long, Money> current = expensesByCategory(from, through);
        Map<Long, Money> before = expensesByCategory(previousFrom, previousThrough);
        Set<Long> ids = new LinkedHashSet<>(current.keySet());
        ids.addAll(before.keySet());
        List<CategoryChange> changes = new ArrayList<>();
        for (long id : ids) {
            categories.find(id).ifPresent(category -> changes.add(new CategoryChange(category,
                    new Change(current.getOrDefault(id, Money.ZERO), before.getOrDefault(id, Money.ZERO)))));
        }
        changes.sort(Comparator
                .comparingLong((CategoryChange change) -> change.change().current().halere()).reversed()
                .thenComparing(Comparator.comparingLong(
                        (CategoryChange change) -> change.change().previous().halere()).reversed())
                .thenComparing(change -> change.category().name()));
        return new Comparison(from, through, previousFrom, previousThrough, changes);
    }

    private Map<Long, Money> expensesByCategory(LocalDate from, LocalDate through) {
        Map<Long, Money> totals = new HashMap<>();
        for (CategoryTotal total : transactions.categoryTotals(from, through)) {
            if (total.type() == TransactionType.EXPENSE) {
                totals.merge(total.categoryId(), total.total(), Money::plus);
            }
        }
        return totals;
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

    // --- Recurring Templates ---

    /** Creates a Recurring Template in CZK; its due occurrences appear on the next generation. */
    public RecurringTemplate createRecurringTemplate(RecurringTemplateDetails details) {
        validate(details);
        return recurringTemplates.add(details);
    }

    /**
     * Generates, as Transactions, every occurrence that came due after the template's last generated one (or
     * from its start) up to today. Run on startup and when the date changes. Each template + occurrence date is
     * generated at most once, even if its Transaction was deleted since.
     */
    public void generateDueOccurrences() {
        LocalDate today = clock.today();
        for (RecurringTemplate template : recurringTemplates.findAll()) {
            RecurringTemplateDetails details = template.details();
            Optional<LocalDate> last = recurringTemplates.lastOccurrence(template.id());
            for (LocalDate date : details.occurrencesThrough(today)) {
                if (last.isPresent() && !date.isAfter(last.get())) {
                    continue;
                }
                Transaction generated = transactions.add(new TransactionDetails(
                        date, details.type(), details.categoryId(), details.amount(), details.note()));
                recurringTemplates.addOccurrence(template.id(), date, generated.id());
            }
        }
    }

    /** The ids of Transactions generated from Recurring Templates, so the Transaction list can mark them. */
    public Set<Long> generatedTransactionIds() {
        return recurringTemplates.generatedTransactionIds();
    }

    /** All Recurring Templates in the order they were created. */
    public List<RecurringTemplate> recurringTemplates() {
        return recurringTemplates.findAll();
    }

    private void validate(RecurringTemplateDetails details) {
        Category category = categories.find(details.categoryId()).orElseThrow(
                () -> new InvalidRecurringTemplateException(InvalidRecurringTemplateException.Problem.UNKNOWN_CATEGORY));
        if (category.type() != details.type()) {
            throw new InvalidRecurringTemplateException(InvalidRecurringTemplateException.Problem.CATEGORY_OF_OTHER_TYPE);
        }
        if (details.amount().halere() <= 0) {
            throw new InvalidRecurringTemplateException(InvalidRecurringTemplateException.Problem.AMOUNT_NOT_POSITIVE);
        }
        if (details.dayOfMonth() < 1 || details.dayOfMonth() > 31) {
            throw new InvalidRecurringTemplateException(InvalidRecurringTemplateException.Problem.DAY_OUT_OF_RANGE);
        }
        if (details.endDate().filter(end -> end.isBefore(details.startDate())).isPresent()) {
            throw new InvalidRecurringTemplateException(InvalidRecurringTemplateException.Problem.END_BEFORE_START);
        }
    }
}
