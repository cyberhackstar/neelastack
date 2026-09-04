-- Neelastack platform - client-facing quotation accept/reject via secure link

ALTER TABLE quotations ADD COLUMN public_token VARCHAR(36);
ALTER TABLE quotations ADD COLUMN response_reason VARCHAR(1000);
ALTER TABLE quotations ADD COLUMN responded_at TIMESTAMP;

-- Backfill any existing rows with a random token before enforcing NOT NULL/UNIQUE
UPDATE quotations SET public_token = gen_random_uuid()::text WHERE public_token IS NULL;

ALTER TABLE quotations ALTER COLUMN public_token SET NOT NULL;
ALTER TABLE quotations ADD CONSTRAINT uq_quotations_public_token UNIQUE (public_token);
