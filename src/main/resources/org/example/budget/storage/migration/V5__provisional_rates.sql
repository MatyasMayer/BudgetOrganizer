-- 1 when a foreign-currency Transaction uses a Provisional Rate (a stand-in for the ČNB Rate of its date,
-- which couldn't be fetched). Refreshing Provisional Rates replaces the rate and resets it to 0.
ALTER TABLE transactions ADD COLUMN provisional_rate INTEGER NOT NULL DEFAULT 0;
