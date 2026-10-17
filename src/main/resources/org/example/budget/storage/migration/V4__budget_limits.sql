-- Budget Limits of Expense Categories. month is yyyy-MM for a Limit Override, NULL for the Default Limit.
-- amount is in haléře.
CREATE TABLE budget_limits (
    category_id INTEGER NOT NULL REFERENCES categories (id),
    month       TEXT,
    amount      INTEGER NOT NULL CHECK (amount > 0)
);

-- At most one Default Limit and one Limit Override per Month for each Category.
CREATE UNIQUE INDEX budget_limits_category_month ON budget_limits (category_id, IFNULL(month, ''));
