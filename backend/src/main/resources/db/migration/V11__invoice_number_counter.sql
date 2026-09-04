-- Race-free invoice numbering.
--
-- Replaces the old "count(*) + 1, retry on unique-constraint collision" approach:
-- that scheme worked, but under concurrent invoice creation it could burn several
-- retries (or exhaust them) before landing on a free number. A single-row
-- UPSERT ... RETURNING is atomic at the statement level in Postgres, so two
-- concurrent transactions incrementing the same year's counter are naturally
-- serialized by the row lock the UPDATE takes — no retry loop needed.
--
-- One row per calendar year, matching the existing "NLS-<year>-0001" format.
CREATE TABLE invoice_number_counters (
    year        INT PRIMARY KEY,
    last_value  BIGINT NOT NULL DEFAULT 0
);

-- Seed the current year's counter from whatever invoice numbers already exist,
-- so numbering picks up where the old count-based scheme left off instead of
-- restarting at 1 and colliding with existing rows.
INSERT INTO invoice_number_counters (year, last_value)
SELECT
    CAST(split_part(invoice_number, '-', 2) AS INT) AS year,
    MAX(CAST(split_part(invoice_number, '-', 3) AS BIGINT)) AS last_value
FROM invoices
WHERE invoice_number ~ '^NLS-[0-9]{4}-[0-9]+$'
GROUP BY CAST(split_part(invoice_number, '-', 2) AS INT)
ON CONFLICT (year) DO UPDATE SET last_value = GREATEST(invoice_number_counters.last_value, EXCLUDED.last_value);
