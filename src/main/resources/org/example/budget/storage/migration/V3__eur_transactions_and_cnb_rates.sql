-- Cache of fetched ČNB Rates. date is the date the rate is used for (ISO yyyy-MM-dd); valid_for is the
-- fixing it came from, earlier for weekends and holidays. czk_per_unit is a plain decimal string.
CREATE TABLE cnb_rates (
    date         TEXT NOT NULL,
    currency     TEXT NOT NULL,
    valid_for    TEXT NOT NULL,
    czk_per_unit TEXT NOT NULL,
    PRIMARY KEY (date, currency)
);

-- A foreign-currency Transaction keeps its Original Amount (in cents), the ČNB Rate used and the optional
-- Charged Amount (in haléře). amount stays the effective CZK amount. All NULL for a CZK Transaction.
ALTER TABLE transactions ADD COLUMN original_currency TEXT;
ALTER TABLE transactions ADD COLUMN original_amount INTEGER;
ALTER TABLE transactions ADD COLUMN cnb_rate TEXT;
ALTER TABLE transactions ADD COLUMN cnb_rate_valid_for TEXT;
ALTER TABLE transactions ADD COLUMN charged_amount INTEGER;
