-- Premium proposal presentation fields. Nullable so existing quotations remain valid.
ALTER TABLE quotations ADD COLUMN IF NOT EXISTS executive_summary TEXT;
ALTER TABLE quotations ADD COLUMN IF NOT EXISTS deliverables TEXT;
ALTER TABLE quotations ADD COLUMN IF NOT EXISTS timeline TEXT;
ALTER TABLE quotations ADD COLUMN IF NOT EXISTS payment_terms TEXT;
ALTER TABLE quotations ADD COLUMN IF NOT EXISTS assumptions TEXT;
ALTER TABLE quotations ADD COLUMN IF NOT EXISTS exclusions TEXT;
ALTER TABLE quotations ADD COLUMN IF NOT EXISTS next_steps TEXT;
