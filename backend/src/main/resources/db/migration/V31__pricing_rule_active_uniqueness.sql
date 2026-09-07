-- The "one active rule per service_key" invariant was previously enforced only in
-- PricingRuleService#deactivateOtherVersions (application code, read-then-write). Two
-- concurrent admin requests creating/activating a rule for the same service_key could both
-- pass that check before either commits, leaving two active rows for the same key --
-- EstimateCalculatorService's findFirstByServiceKeyAndActiveTrueOrderByVersionDesc would then
-- return whichever one Postgres happens to order first, silently. A partial unique index makes
-- this impossible at the database level regardless of application-layer races; the
-- application-layer check in PricingRuleService remains as defense-in-depth (it gives a clean
-- 409/validation error in the non-racing case instead of a raw constraint-violation exception).
CREATE UNIQUE INDEX IF NOT EXISTS uq_pricing_rules_active_service_key
    ON pricing_rules (service_key)
    WHERE active = TRUE;
