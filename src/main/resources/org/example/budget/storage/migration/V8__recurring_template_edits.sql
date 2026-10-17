-- The day a Recurring Template was last edited (ISO yyyy-MM-dd), NULL if never. Generation never fills in
-- Months before the edit, so an edit only changes occurrences generated from then on.
ALTER TABLE recurring_templates ADD COLUMN edited_on TEXT;
