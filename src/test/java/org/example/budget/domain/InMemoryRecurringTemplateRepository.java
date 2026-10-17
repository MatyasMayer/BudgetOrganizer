package org.example.budget.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** In-memory fake of {@link RecurringTemplateRepository}, kept faithful by {@code RecurringTemplateRepositoryContract}. */
public class InMemoryRecurringTemplateRepository implements RecurringTemplateRepository {

    private final Map<Long, RecurringTemplate> templates = new LinkedHashMap<>();
    private final Map<Long, TreeMap<LocalDate, Long>> occurrences = new HashMap<>();
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
    public List<RecurringTemplate> findAll() {
        return new ArrayList<>(templates.values());
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
}
