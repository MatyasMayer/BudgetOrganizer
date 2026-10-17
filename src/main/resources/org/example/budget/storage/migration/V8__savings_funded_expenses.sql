-- 1 marks a Savings-Funded Expense: paid from Savings, so it's left out of its Month's Leftover and its
-- Category's Budget Limit. Always 0 for an Income.
ALTER TABLE transactions ADD COLUMN savings_funded INTEGER NOT NULL DEFAULT 0;
