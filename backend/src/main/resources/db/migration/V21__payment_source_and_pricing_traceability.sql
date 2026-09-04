-- Commercial engine & financial hardening (Section 4), two small precise additions:
--
-- 1. Payment reconciliation source tagging. PaymentReconciliationService and the live
--    Razorpay webhook both funnel through InvoiceService.markPaidFromWebhook today with
--    no record of *which* path actually flipped an invoice to PAID. Added as an Invoice
--    column now (audit_logs doesn't exist yet -- that's Section 2, built after this) so
--    there's exactly one source of truth for "how did this get marked paid" from day
--    one; when Section 2 adds audit_logs, PAYMENT_MARKED_PAID entries can read this
--    column rather than duplicating it.
--
-- 2. Pricing version traceability. Quotation.totalAmount / lineItems are entered by an
--    admin (QuotationService#create), not auto-derived from an active PricingRule the
--    way the public estimator (EstimateCalculatorService) is -- so there is no existing
--    automatic link between a quotation and the pricing rule that informed it. These
--    columns let an admin optionally record which PricingRule (and which version of it,
--    snapshotted at creation time since the rule row can be edited later) backed a given
--    quotation. Both nullable and backfilled NULL: historical quotations genuinely don't
--    have this data, and a NULL pricing_rule_id on a new quotation just means "priced
--    without reference to a configured rule" (e.g. a fully custom/negotiated deal),
--    which is a real, valid case -- not an error to paper over.

ALTER TABLE invoices ADD COLUMN payment_source VARCHAR(20);
ALTER TABLE quotations ADD COLUMN pricing_rule_id UUID REFERENCES pricing_rules (id);
ALTER TABLE quotations ADD COLUMN pricing_rule_version INT;
