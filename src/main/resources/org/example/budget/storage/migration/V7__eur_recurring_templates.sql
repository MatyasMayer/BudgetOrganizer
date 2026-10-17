-- A Recurring Template in a foreign currency keeps its Original Amount (in cents); amount is then 0, since each
-- occurrence is converted at the ČNB Rate of its own due date. Both NULL for a CZK template.
ALTER TABLE recurring_templates ADD COLUMN original_currency TEXT;
ALTER TABLE recurring_templates ADD COLUMN original_amount INTEGER;
