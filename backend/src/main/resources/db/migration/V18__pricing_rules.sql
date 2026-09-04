-- Dynamic Commercial Pricing Engine (P0 fix): replaces the hardcoded, placeholder
-- ranges that used to live inside EstimateCalculatorService's Java source
-- (e.g. "audit/review -> 25,000-75,000") with database-backed rules the admin
-- portal can edit without a redeploy. Row-per-service-category, with `active`
-- and `version` so a rule can be revised (insert a new version, deactivate the
-- old one) while keeping history for pricing-change audit purposes.
CREATE TABLE pricing_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Matches the category keys EstimateCalculatorService resolves an intake to
    -- (see EstimateCalculatorService#resolveServiceKey) — not a free-text label.
    service_key VARCHAR(60) NOT NULL,
    base_low NUMERIC(12, 2) NOT NULL,
    -- NULL means "no responsible automatic upper bound" (custom/enterprise scope) —
    -- the calculator falls back to a scoped-conversation response, same as before.
    base_high NUMERIC(12, 2),
    -- Multipliers applied on top of the base range — see EstimateCalculatorService
    -- for exactly how each is combined. Stored as decimal fractions (0.20 = +20%),
    -- not percentages, to match how they're used directly in BigDecimal math.
    complexity_factor NUMERIC(6, 3) NOT NULL DEFAULT 0,
    scale_factor NUMERIC(6, 3) NOT NULL DEFAULT 0,
    integration_factor NUMERIC(6, 3) NOT NULL DEFAULT 0,
    urgency_factor NUMERIC(6, 3) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version INT NOT NULL DEFAULT 1,
    notes VARCHAR(300),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Only one active rule per service_key is meaningful at a time; enforced at the
-- application layer (PricingRuleService) rather than a DB constraint so an admin
-- can stage a draft new version before flipping it active, same pattern used for
-- published/draft content elsewhere (services, projects, blog).
CREATE INDEX idx_pricing_rules_service_key_active ON pricing_rules (service_key, active);

-- Seed rows carry forward the previous hardcoded numbers as a starting point only —
-- an admin is expected to revise these to the practice's real commercial strategy
-- (master prompt section 87 already flagged the old numbers as placeholders).
INSERT INTO pricing_rules
    (service_key, base_low, base_high, complexity_factor, scale_factor, integration_factor, urgency_factor, active, version, notes)
VALUES
    ('audit-review', 25000, 75000, 0.10, 0.15, 0.15, 0.10, TRUE, 1, 'Code audits / architecture / performance reviews'),
    ('api-backend', 75000, 200000, 0.15, 0.20, 0.15, 0.10, TRUE, 1, 'API / backend-only engagements'),
    ('fix', 25000, 150000, 0.15, 0.15, 0.15, 0.15, TRUE, 1, 'Fixes and feature work on an existing app'),
    ('full-stack', 150000, 500000, 0.20, 0.25, 0.15, 0.10, TRUE, 1, 'Default: new full-stack web application'),
    ('enterprise-platform', 500000, NULL, 0, 0, 0, 0, TRUE, 1, 'Enterprise/platform/marketplace scope — no automatic upper bound, always a scoped conversation');
