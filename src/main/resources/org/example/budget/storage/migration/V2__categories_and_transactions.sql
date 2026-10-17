-- Categories: Income and Expense lists; system = 1 marks From Savings.
CREATE TABLE categories (
    id     INTEGER PRIMARY KEY AUTOINCREMENT,
    name   TEXT    NOT NULL,
    type   TEXT    NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    system INTEGER NOT NULL DEFAULT 0 CHECK (system IN (0, 1))
);

-- Transactions in CZK. amount is in haléře; a negative Expense is a Refund. date is ISO yyyy-MM-dd.
CREATE TABLE transactions (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    date        TEXT    NOT NULL,
    type        TEXT    NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    category_id INTEGER NOT NULL REFERENCES categories (id),
    amount      INTEGER NOT NULL,
    note        TEXT
);

CREATE INDEX transactions_date ON transactions (date);
CREATE INDEX transactions_category ON transactions (category_id);
