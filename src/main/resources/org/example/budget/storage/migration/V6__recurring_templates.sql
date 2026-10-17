-- Recurring Templates in CZK. amount is in haléře; dates are ISO yyyy-MM-dd; end_date NULL means no end.
CREATE TABLE recurring_templates (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    type         TEXT    NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    category_id  INTEGER NOT NULL REFERENCES categories (id),
    amount       INTEGER NOT NULL,
    note         TEXT,
    frequency    TEXT    NOT NULL CHECK (frequency IN ('MONTHLY', 'YEARLY')),
    day_of_month INTEGER NOT NULL CHECK (day_of_month BETWEEN 1 AND 31),
    start_date   TEXT    NOT NULL,
    end_date     TEXT
);

CREATE INDEX recurring_templates_category ON recurring_templates (category_id);

-- Each template + occurrence date is generated at most once. The row stays when the user deletes the
-- generated Transaction (transaction_id becomes NULL), so the occurrence is never generated again.
CREATE TABLE generated_occurrences (
    template_id     INTEGER NOT NULL REFERENCES recurring_templates (id),
    occurrence_date TEXT    NOT NULL,
    transaction_id  INTEGER REFERENCES transactions (id) ON DELETE SET NULL,
    PRIMARY KEY (template_id, occurrence_date)
);

CREATE INDEX generated_occurrences_transaction ON generated_occurrences (transaction_id);
