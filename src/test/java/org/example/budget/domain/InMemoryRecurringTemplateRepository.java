package org.example.budget.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** In-memory fake of {@link RecurringTemplateRepository}, kept faithful by {@code RecurringTemplateRepositoryContract}. */
public class InMemoryRecurringTemplateRepository implements RecurringTemplateRepository {

    private final Map<Long, RecurringTemplate> templates = new LinkedHashMap<>();
    private final Map<Long, TreeMap<LocalDate, Long>> occurrences = new HashMap<>();
    private final Map<Long, LocalDate> edits = new HashMap<>();
    private long nextId = 1;

    @Override
    public RecurringTemplate add(RecurringTemplateDetails details) {
        RecurringTemplate template = new RecurringTemplate(nextId++, details);
        templates.put(template.id(), template);
        return template;
    }

    @Override
    public Optional<RecurringTemplate> find(long id) {
        return Optional.ofNullable(templates.get(id));
    }

    @Override
    public void update(RecurringTemplate template, LocalDate editedOn) {
        if (templates.replace(template.id(), template) != null) {
            edits.put(template.id(), editedOn);
        }
    }

    @Override
    public Optional<LocalDate> lastEdited(long templateId) {
        return Optional.ofNullable(edits.get(templateId));
    }

    @Override
    public List<RecurringTemplate> findAll() {
        return new ArrayList<>(templates.values());
    }

    @Override
    public void moveToCategory(long fromCategoryId, long toCategoryId) {
        templates.replaceAll((id, template) -> {
            RecurringTemplateDetails d = template.details();
            if (d.categoryId() != fromCategoryId) {
                return template;
            }
            return new RecurringTemplate(id, new RecurringTemplateDetails(d.type(), toCategoryId, d.amount(),
                    d.note(), d.frequency(), d.dayOfMonth(), d.startDate(), d.endDate()));
        });
    }

    @Override
    public void addOccurrence(long templateId, LocalDate occurrenceDate, long transactionId) {
        Map<LocalDate, Long> dates = occurrences.computeIfAbsent(templateId, id -> new TreeMap<>());
        if (dates.containsKey(occurrenceDate)) {
            throw new IllegalStateException("Occurrence already generated: " + templateId + " " + occurrenceDate);
        }
        dates.put(occurrenceDate, transactionId);
    }

    @Override
    public Optional<LocalDate> lastOccurrence(long templateId) {
        TreeMap<LocalDate, Long> dates = occurrences.get(templateId);
        return dates == null ? Optional.empty() : Optional.of(dates.lastKey());
    }

    @Override
    public Set<Long> generatedTransactionIds() {
        return occurrences.values().stream()
                .flatMap(dates -> dates.values().stream())
                .collect(Collectors.toUnmodifiableSet());
    }
}
