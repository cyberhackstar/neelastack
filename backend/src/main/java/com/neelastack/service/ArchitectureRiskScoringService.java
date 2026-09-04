package com.neelastack.service;

import lombok.Builder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Powers the "Instant Architecture Risk Score" lead magnet at /audit-preview (module
 * 1 of the Client Acquisition & High-Ticket Conversion Engine).
 *
 * The score is a transparent, rule-based function of exactly what the visitor
 * checked — the same "disclosed heuristic, not a manufactured authority number"
 * approach this codebase already uses for {@code LeadScoringService} and documents
 * explicitly in {@code ExecutiveReportPdfService}. It is NOT a claim that Neelastack
 * has looked at the visitor's actual code (the free-form architecture-review lead
 * magnet exists for that); it's a self-assessment score derived only from the
 * visitor's own selections, and the report always says so.
 *
 * Findings are static, general engineering guidance keyed to well-known scaling
 * bottlenecks — never a claim about the visitor's specific system, and never a
 * fabricated metric. Real, verified project metrics belong on the Project entity
 * (see Project#keyMetrics) — nothing here invents numbers.
 */
@Service
public class ArchitectureRiskScoringService {

    @Builder
    public record Finding(String key, String title, int weight, String severity, String summary, String recommendation) {}

    @Builder
    public record PreviewResult(int riskScore, String riskLevel, List<String> teaserFindings, int lockedFindingsCount) {}

    @Builder
    public record FullReport(int riskScore, String riskLevel, List<Finding> findings) {}

    /** Known scaling bottlenecks the /audit-preview checklist offers, matched by key.
     *  Deliberately the same handful the marketing brief calls out (DB connection
     *  pooling, SSR hydration delay, payment concurrency races) plus a few more
     *  common ones, so the tool stays honest about only scoring what's asked. */
    private static final Map<String, Finding> CATALOG;

    static {
        Map<String, Finding> catalog = new LinkedHashMap<>();
        catalog.put("DB_CONNECTION_POOLING", Finding.builder()
                .key("DB_CONNECTION_POOLING").title("Database connection pool sizing").weight(18).severity("HIGH")
                .summary("Undersized or misconfigured connection pools are one of the most common causes of "
                        + "cascading timeouts under real traffic — requests queue for a connection instead of failing fast.")
                .recommendation("Size the pool from actual concurrency and DB max_connections, not a framework "
                        + "default, and set explicit connection/statement timeouts so a slow query degrades one "
                        + "request instead of starving the whole pool.")
                .build());
        catalog.put("SSR_HYDRATION_DELAY", Finding.builder()
                .key("SSR_HYDRATION_DELAY").title("SSR hydration delay").weight(12).severity("MEDIUM")
                .summary("A server-rendered page that takes noticeably long to become interactive hurts both "
                        + "perceived performance and Core Web Vitals (TBT/INP), which SEO ranking and conversion "
                        + "both depend on.")
                .recommendation("Profile time-to-interactive specifically (not just server render time), and "
                        + "look for large client-only bundles or browser-only APIs executing during hydration.")
                .build());
        catalog.put("PAYMENT_CONCURRENCY", Finding.builder()
                .key("PAYMENT_CONCURRENCY").title("Payment concurrency / race conditions").weight(22).severity("CRITICAL")
                .summary("Payment confirmation paths that read-then-write invoice status without row-level "
                        + "locking are exposed to double-charge, double-fulfilment, or a valid payment being "
                        + "overwritten by a stale duplicate request.")
                .recommendation("Use pessimistic row locks (or an equivalent atomic conditional update) around "
                        + "every status transition, make webhook processing idempotent by provider event ID, and "
                        + "add concurrency tests that assert only one transition ever wins a race.")
                .build());
        catalog.put("N_PLUS_ONE_QUERIES", Finding.builder()
                .key("N_PLUS_ONE_QUERIES").title("N+1 query patterns on list endpoints").weight(10).severity("MEDIUM")
                .summary("List endpoints that lazily fetch a child collection or aggregate per parent row scale "
                        + "linearly in query count with page size — fine at demo data volumes, painful in production.")
                .recommendation("Audit list/aggregate endpoints for batch queries, projections, or entity graphs "
                        + "instead of one query per row, and keep pagination mandatory on anything unbounded.")
                .build());
        catalog.put("CACHE_INVALIDATION", Finding.builder()
                .key("CACHE_INVALIDATION").title("Cache invalidation correctness").weight(10).severity("MEDIUM")
                .summary("A cache that isn't explicitly invalidated on the write path that changes its source "
                        + "data serves stale content indefinitely, and a cache outage that isn't handled gracefully "
                        + "can take core functionality down with it.")
                .recommendation("Tie every cache eviction to the specific write that changes that data, use typed "
                        + "(not generic/polymorphic) serializers, and make sure a cache-layer failure degrades "
                        + "rather than breaks core transactional flows.")
                .build());
        catalog.put("JWT_REFRESH_RACE", Finding.builder()
                .key("JWT_REFRESH_RACE").title("Refresh-token rotation races") .weight(15).severity("HIGH")
                .summary("A refresh-token flow implemented as read-check-delete instead of an atomic claim is "
                        + "vulnerable to concurrent-refresh races that can let a revoked or reused token slip "
                        + "through, or lock a legitimate user out.")
                .recommendation("Rotate refresh tokens with an atomic primitive (GETDEL/SETNX/Lua script or a DB "
                        + "transaction), track token families, and add a test that fires concurrent refreshes at "
                        + "the same token and asserts exactly one succeeds.")
                .build());
        catalog.put("WEBHOOK_IDEMPOTENCY", Finding.builder()
                .key("WEBHOOK_IDEMPOTENCY").title("Webhook idempotency").weight(14).severity("HIGH")
                .summary("Providers (payments, email, etc.) retry webhooks on any ambiguous response. Processing "
                        + "the same event twice without a persisted event-ID check can double-apply a state change.")
                .recommendation("Persist the provider's event ID before processing, treat a duplicate as a no-op "
                        + "returning the prior result, and verify the request signature on every delivery.")
                .build());
        catalog.put("AUTH_RATE_LIMITING", Finding.builder()
                .key("AUTH_RATE_LIMITING").title("Auth endpoint rate limiting") .weight(9).severity("MEDIUM")
                .summary("Login, password-reset, and OTP endpoints without rate limiting are exposed to credential "
                        + "stuffing and brute-force attempts at effectively unlimited speed.")
                .recommendation("Apply a distributed (Redis-backed) fixed- or sliding-window limiter per IP and "
                        + "per account on every auth-adjacent endpoint, and fail open (not closed) if the limiter's "
                        + "own backing store is unavailable.")
                .build());
        CATALOG = Map.copyOf(catalog);
    }

    /** No-PII preview: computes the score and returns two teaser bullets (the two
     *  highest-weight findings, summary only) — enough real value to justify the
     *  gate, without giving away the full report for free. */
    public PreviewResult preview(List<String> techStack, List<String> bottlenecks) {
        List<Finding> matched = matchFindings(bottlenecks);
        int score = computeScore(techStack, matched);

        List<String> teaser = matched.stream()
                .sorted((a, b) -> Integer.compare(b.weight(), a.weight()))
                .limit(2)
                .map(f -> f.title() + " — " + f.severity().toLowerCase(Locale.ROOT) + " priority")
                .toList();

        int lockedCount = Math.max(0, matched.size() - teaser.size());

        return PreviewResult.builder()
                .riskScore(score)
                .riskLevel(riskLevel(score))
                .teaserFindings(teaser)
                .lockedFindingsCount(lockedCount)
                .build();
    }

    /** Full, unlocked breakdown — sent by email and shown on-page immediately after
     *  the visitor provides name/email/company (InquiryService#submitAuditPreview). */
    public FullReport fullReport(List<String> techStack, List<String> bottlenecks) {
        List<Finding> matched = matchFindings(bottlenecks);
        int score = computeScore(techStack, matched);
        return FullReport.builder()
                .riskScore(score)
                .riskLevel(riskLevel(score))
                .findings(matched)
                .build();
    }

    private List<Finding> matchFindings(List<String> bottlenecks) {
        if (bottlenecks == null || bottlenecks.isEmpty()) {
            return List.of();
        }
        return bottlenecks.stream()
                .map(b -> CATALOG.get(b == null ? null : b.trim().toUpperCase(Locale.ROOT)))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * Score = sum of the selected findings' weights, plus a modest complexity
     * bonus for a multi-technology stack (more moving parts, more places for a
     * given bottleneck to bite), capped at 100. Every input to this number is
     * something the visitor themselves selected — nothing here is inferred about
     * their actual codebase, and the report says exactly that.
     */
    private int computeScore(List<String> techStack, List<Finding> matched) {
        int base = matched.stream().mapToInt(Finding::weight).sum();
        int stackSize = techStack == null ? 0 : (int) techStack.stream().filter(s -> s != null && !s.isBlank()).count();
        int complexityBonus = Math.min(10, Math.max(0, stackSize - 2) * 3);
        return Math.min(100, base + complexityBonus);
    }

    private String riskLevel(int score) {
        if (score >= 70) return "CRITICAL";
        if (score >= 45) return "HIGH";
        if (score >= 20) return "MODERATE";
        return "LOW";
    }
}
