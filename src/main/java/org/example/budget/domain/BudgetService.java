package org.example.budget.domain;

import org.example.budget.domain.InvalidTransactionException.Problem;

import java.time.LocalDate;
import java.util.List;
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

    public BudgetService(SettingsRepository settings, CategoryRepository categories,
                         TransactionRepository transactions, Clock clock) {
        this.settings = settings;
        this.categories = categories;
        this.transactions = transactions;
        this.clock = clock;
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

    private void validate(TransactionDetails details) {
        Category category = categories.find(details.categoryId())
                .orElseThrow(() -> new InvalidTransactionException(Problem.UNKNOWN_CATEGORY));
        if (category.type() != details.type()) {
            throw new InvalidTransactionException(Problem.CATEGORY_OF_OTHER_TYPE);
        }
        long amount = details.amount().halere();
        if (amount == 0) {
            throw new InvalidTransactionException(Problem.ZERO_AMOUNT);
        }
        if (amount < 0 && details.type() == TransactionType.INCOME) {
            throw new InvalidTransactionException(Problem.NEGATIVE_INCOME);
        }
    }
}
