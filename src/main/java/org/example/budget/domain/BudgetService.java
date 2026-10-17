package org.example.budget.domain;

import org.example.budget.domain.InvalidTransactionException.Problem;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
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
    private final Clock clock;

    public BudgetService(SettingsRepository settings, CategoryRepository categories,
                         TransactionRepository transactions, RecurringTemplateRepository recurringTemplates,
                         Clock clock) {
        this.settings = settings;
        this.categories = categories;
        this.transactions = transactions;
        this.recurringTemplates = recurringTemplates;
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
