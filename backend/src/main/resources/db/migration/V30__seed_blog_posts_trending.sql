-- =============================================================================
-- Neelastack Platform: Trending Engineering Blog Seed Migration
-- High-intent, keyword-rich topics targeting current enterprise search volumes:
-- Java 21 Virtual Threads, Angular Signals, PostgreSQL Index Optimization,
-- Distributed Tracing, API Rate Limiting, and Resilient Outbox Patterns.
-- =============================================================================

INSERT INTO blog_posts
    (id, title, slug, excerpt, content, cover_image_url, author_name, category,
     meta_title, meta_description, published, published_at)
VALUES
(
    gen_random_uuid(),
    'Java 21 Virtual Threads in Spring Boot 3: Benchmark, Pitfalls, and Pinning',
    'java-21-virtual-threads-spring-boot-benchmarks',
    'Project Loom promises massive concurrency with zero reactive refactoring. Here is what actually happens when you flip the switch on Tomcat in Spring Boot 3, and how to detect thread pinning.',
    E'<p>With the release of Java 21 LTS and Spring Boot 3.2+, switching your web application from traditional platform threads to <strong>Project Loom Virtual Threads</strong> requires just a single configuration property: <code>spring.threads.virtual.enabled=true</code>. But treating virtual threads as a universal performance silver bullet can lead to severe latency traps if your dependencies violate thread-carrier constraints.</p>\n' ||
    E'<h2>The Promise: Millions of Concurrent HTTP Sockets</h2>\n' ||
    E'<p>Traditional JVM web servers allocate one OS-level platform thread per request (consuming roughly 1MB of stack memory per thread). Virtual threads are lightweight user-mode threads managed by the JVM runtime that mount onto a small pool of carrier OS threads. When a virtual thread encounters blocking I/O (such as a database query or an external HTTP call), the JVM unmounts it, freeing the underlying carrier thread to process other tasks.</p>\n' ||
    E'<h2>The Pitfall: Carrier Thread Pinning</h2>\n' ||
    E'<p>A virtual thread becomes <em>pinned</em> to its carrier thread whenever blocking operations occur inside a <code>synchronized</code> block or across a native JNI call. When pinned, the underlying carrier thread cannot be unmounted, rapidly exhausting the carrier worker pool and degrading throughput below that of a standard platform-thread pool:</p>\n' ||
    E'<pre><code class="language-java">// BAD: Causes carrier thread pinning during I/O\n' ||
    E'public synchronized byte[] fetchExternalAsset(String url) {\n' ||
    E'    return restClient.get().uri(url).retrieve().body(byte[].class); // Blocks carrier!\n' ||
    E'}\n\n' ||
    E'// GOOD: Replaced with ReentrantLock\n' ||
    E'private final ReentrantLock lock = new ReentrantLock();\n' ||
    E'public byte[] fetchExternalAssetSafe(String url) {\n' ||
    E'    lock.lock();\n' ||
    E'    try {\n' ||
    E'        return restClient.get().uri(url).retrieve().body(byte[].class);\n' ||
    E'    } finally {\n' ||
    E'        lock.unlock();\n' ||
    E'    }\n' ||
    E'}\n' ||
    E'</code></pre>\n' ||
    E'<h2>How to Profile and Detect Pinning in Production</h2>\n' ||
    E'<p>Add the following JVM flag to your startup container environment to track pinning events in real time:</p>\n' ||
    E'<pre><code class="language-bash">-Djdk.tracePinnedThreads=short\n' ||
    E'</code></pre>\n' ||
    E'<p>Virtual threads are transformative for blocking I/O applications, but JDBC connection pool sizes (such as HikariCP) still bound your database concurrency. Keep HikariCP pool sizes bounded to actual database core capacity rather than scaling pool limits to match virtual thread counts.</p>',
    NULL,
    'Neelastack Engineering',
    'Backend',
    'Java 21 Virtual Threads in Spring Boot 3 — Neelastack',
    'Benchmark analysis, carrier thread pinning risks, and real-world Spring Boot 3 configurations using Project Loom virtual threads.',
    TRUE,
    now() - INTERVAL '4 days'
),
(gen_random_uuid(),
    'Angular Signals vs RxJS: When to Replace Observables in Production',
    'angular-signals-vs-rxjs-production-guide',
    'Angular Signals offer fine-grained reactivity and zoneless change detection. A practical guide on when to use Signals for state and when to keep RxJS for asynchronous event streams.',
    E'<p>The introduction of <strong>Angular Signals</strong> represents the biggest architectural shift in the Angular ecosystem since the release of standalone components. By providing fine-grained reactivity with zero Zone.js overhead, Signals eliminate unnecessary component tree re-evaluations. However, many teams struggle with knowing where Signals end and RxJS streams begin.</p>\n' ||
    E'<h2>Core Philosophy: Synchronous State vs Asynchronous Streams</h2>\n' ||
    E'<p>The most effective mental model is clear: <strong>Signals represent state that exists at a specific point in time</strong>, while <strong>RxJS represents streams of events over time</strong>.</p>\n' ||
    E'<ul>\n' ||
    E'<li><strong>Use Signals for:</strong> Component state, computed derived calculations, template bindings, user form input values, and UI visibility flags.</li>\n' ||
    E'<li><strong>Keep RxJS for:</strong> Complex asynchronous orchestration, debounced search inputs, polling loops, WebSocket event multiplexing, and retry/backoff network handling.</li>\n' ||
    E'</ul>\n' ||
    E'<h2>Bridging the Gap: toSignal() and toObservable()</h2>\n' ||
    E'<p>In modern Angular applications, backend data fetching typically starts as an RxJS Observable that converts into a template-friendly Signal at the boundary:</p>\n' ||
    E'<pre><code class="language-typescript">import { Component, inject } from ''@angular/core'';\n' ||
    E'import { toSignal } from ''@angular/core/rxjs-interop'';\n' ||
    E'import { SolutionService } from ''@core/services/solution.service'';\n\n' ||
    E'@Component({\n' ||
    E'  selector: ''app-solution-dashboard'',\n' ||
    E'  standalone: true,\n' ||
    E'  template: `\n' ||
    E'    &lt;div class="solution-grid"&gt;\n' ||
    E'      @for (item of solutions(); track item.id) {\n' ||
    E'        &lt;div class="card"&gt;{{ item.title }} - {{ item.category }}&lt;/div&gt;\n' ||
    E'      } @empty {\n' ||
    E'        &lt;p&gt;No solutions cataloged.&lt;/p&gt;\n' ||
    E'      }\n' ||
    E'    &lt;/div&gt;\n' ||
    E'  `\n' ||
    E'})\n' ||
    E'export class SolutionDashboardComponent {\n' ||
    E'  private readonly solutionService = inject(SolutionService);\n' ||
    E'  \n' ||
    E'  // Automatically handles subscription teardown on component destroy\n' ||
    E'  protected solutions = toSignal(this.solutionService.getAllActive(), { initialValue: [] });\n' ||
    E'}\n' ||
    E'</code></pre>\n' ||
    E'<p>Using Signals inside templates eliminates the need for the legacy <code>| async</code> pipe and prepares your frontend codebase for completely zoneless, high-efficiency rendering.</p>',
    NULL,
    'Neelastack Engineering',
    'Frontend',
    'Angular Signals vs RxJS: Complete Guide — Neelastack',
    'Learn when to use Angular Signals vs RxJS Observables with practical migration patterns, interop examples, and zoneless performance tuning.',
    TRUE,
    now() - INTERVAL '3 days'
),
(gen_random_uuid(),
    'PostgreSQL Indexing Strategies: B-Tree, BRIN, and Partial Indexes Under Load',
    'postgresql-indexing-strategies-btree-brin-partial',
    'Adding indexes blindly slows down writes and bloats table sizes. How to select the right index type for high-volume logs, audit trails, and multi-tenant queries.',
    E'<p>When a PostgreSQL query begins to slow down, the default impulse is often to slap a composite B-Tree index onto the offending columns. While this may salvage read performance temporarily, unvetted indexing strategies can degrade write performance, inflate table storage, and cause cache churn on write-heavy tables.</p>\n' ||
    E'<h2>1. Partial Indexes: Indexing Only What Matters</h2>\n' ||
    E'<p>In enterprise sales pipelines and billing architectures, most queries filter for active or pending states. Rather than indexing millions of archived or completed records, create a <strong>Partial Index</strong> with a <code>WHERE</code> predicate:</p>\n' ||
    E'<pre><code class="language-sql">-- Standard B-Tree: Indexes all 5,000,000 rows\n' ||
    E'CREATE INDEX idx_invoices_status ON invoices (status);\n\n' ||
    E'-- Partial Index: Indexes only the 2% of rows requiring action\n' ||
    E'CREATE INDEX idx_invoices_pending ON invoices (created_at) \n' ||
    E'WHERE status = ''PENDING'';\n' ||
    E'</code></pre>\n' ||
    E'<p>The partial index consumes a fraction of memory, fits entirely within PostgreSQL <code>shared_buffers</code>, and eliminates indexing overhead during subsequent updates once an invoice transitions to <code>PAID</code>.</p>\n' ||
    E'<h2>2. BRIN Indexes for Massive Append-Only Tables</h2>\n' ||
    E'<p>For append-only records like audit trails (<code>audit_logs</code>) or webhook event histories where records are naturally sorted by time, traditional B-Trees require massive tree node balancings. A <strong>BRIN (Block Range Index)</strong> stores summary minimum and maximum values for physical disk block ranges:</p>\n' ||
    E'<pre><code class="language-sql">-- Consumes under 1% of the disk space of a comparable B-Tree\n' ||
    E'CREATE INDEX idx_audit_logs_timestamp_brin ON audit_logs \n' ||
    E'USING BRIN (timestamp) WITH (pages_per_range = 32);\n' ||
    E'</code></pre>\n' ||
    E'<p>By pairing BRIN indexes with time-series partitions, multi-gigabyte audit tables can be queried across specific date windows without performance degradation.</p>',
    NULL,
    'Neelastack Engineering',
    'Database',
    'PostgreSQL Index Optimization: B-Tree vs BRIN — Neelastack',
    'Deep architectural guide on PostgreSQL indexing: partial indexes, BRIN structures for audit logs, and composite index design rules.',
    TRUE,
    now() - INTERVAL '2 days'
),
(gen_random_uuid(),
    'Distributed Tracing in Spring Boot: Micrometer, OpenTelemetry, and Trace IDs',
    'distributed-tracing-spring-boot-opentelemetry',
    'Debugging asynchronous webhooks and microservice failures without contextual trace IDs is impossible. Here is how we enforce request-id propagation across threads and network boundaries.',
    E'<p>When a distributed operation fails—such as a webhook arriving from a payment gateway, triggering an asynchronous invoice reconciliation job, and updating an analytics counter—diagnosing the failure in standard logs requires correlating distinct events across threads and network boundaries. Without distributed context propagation, root-cause analysis becomes guesswork.</p>\n' ||
    E'<h2>The W3C TraceContext Standard</h2>\n' ||
    E'<p>Modern distributed observability relies on the W3C TraceContext standard, consisting of a <code>traceparent</code> header containing a unique <strong>Trace ID</strong> (identifying the entire end-to-end transaction) and a <strong>Span ID</strong> (identifying the specific hop or sub-operation).</p>\n' ||
    E'<h2>Configuring Micrometer Tracing in Spring Boot 3</h2>\n' ||
    E'<p>Spring Boot 3 replaced Spring Cloud Sleuth with <strong>Micrometer Tracing</strong>. Setting up production-grade correlation requires minimal dependencies:</p>\n' ||
    E'<pre><code class="language-xml">&lt;dependency&gt;\n' ||
    E'    &lt;groupId&gt;io.micrometer&lt;/groupId&gt;\n' ||
    E'    &lt;artifactId&gt;micrometer-tracing-bridge-otel&lt;/artifactId&gt;\n' ||
    E'&lt;/dependency&gt;\n' ||
    E'</code></pre>\n' ||
    E'<h2>Propagating MDC Context to Asynchronous Threads</h2>\n' ||
    E'<p>By default, SLF4J <code>MDC</code> (Mapped Diagnostic Context) is tied to a single thread. When dispatching tasks to an <code>@Async</code> executor, the trace context is lost unless your thread pool is wrapped with a context-aware task decorator:</p>\n' ||
    E'<pre><code class="language-java">@Configuration\n' ||
    E'public class AsyncTracingConfig {\n' ||
    E'    @Bean\n' ||
    E'    public TaskDecorator contextPropagatingDecorator() {\n' ||
    E'        return runnable -&gt; {\n' ||
    E'            Map&lt;String, String&gt; contextMap = MDC.getCopyOfContextMap();\n' ||
    E'            return () -&gt; {\n' ||
    E'                try {\n' ||
    E'                    if (contextMap != null) MDC.setContextMap(contextMap);\n' ||
    E'                    runnable.run();\n' ||
    E'                } finally {\n' ||
    E'                    MDC.clear();\n' ||
    E'                }\n' ||
    E'            };\n' ||
    E'        };\n' ||
    E'    }\n' ||
    E'}\n' ||
    E'</code></pre>\n' ||
    E'<p>This guarantees that every asynchronous operation, database write, and webhook response logs the exact same correlation ID, making troubleshooting on tools like Sentry or Datadog immediate.</p>',
    NULL,
    'Neelastack Engineering',
    'Observability',
    'Spring Boot Distributed Tracing with OpenTelemetry — Neelastack',
    'Implement OpenTelemetry distributed tracing and MDC context propagation across asynchronous executors in Spring Boot 3.',
    TRUE,
    now() - INTERVAL '1 days'
),
(gen_random_uuid(),
    'Rate Limiting REST APIs: Sliding Window vs Token Bucket with Redis',
    'rate-limiting-rest-apis-sliding-window-redis',
    'Protecting authentication and public estimation endpoints against credential stuffing and DoS attacks. Why sliding-window rate limiters beat fixed windows in production.',
    E'<p>Any public endpoint that performs computational work—such as an automated PDF generator, an interactive project estimator, or a login endpoint—is a prime target for automated credential stuffing and resource exhaustion attacks. Choosing the right rate-limiting algorithm is critical to protecting backend infrastructure without impacting legitimate users.</p>\n' ||
    E'<h2>The Flaw of Fixed-Window Counter Algorithms</h2>\n' ||
    E'<p>Fixed-window rate limiting resets the request counter at fixed time intervals (e.g., allow 100 requests per minute resetting at :00). An attacker can exploit the boundaries by sending 100 requests at 00:59 and another 100 requests at 01:01, bursting 200 requests within a two-second window and overwhelming the system.</p>\n' ||
    E'<h2>The Solution: The Sliding Window Log with Redis Sorted Sets</h2>\n' ||
    E'<p>Using a Redis Sorted Set (<code>ZSET</code>), we record each request as a member scored by its millisecond timestamp. This guarantees an accurate rolling window evaluation regardless of when requests arrive:</p>\n' ||
    E'<pre><code class="language-lua">-- Redis Lua Script for Atomic Sliding Window Rate Limiting\n' ||
    E'local key = KEYS[1]\n' ||
    E'local now = tonumber(ARGV[1])\n' ||
    E'local window = tonumber(ARGV[2])\n' ||
    E'local limit = tonumber(ARGV[3])\n' ||
    E'local clearBefore = now - window\n\n' ||
    E'-- Remove timestamps older than current sliding window\n' ||
    E'redis.call(''ZREMRANGEBYSCORE'', key, 0, clearBefore)\n\n' ||
    E'-- Count remaining requests inside rolling window\n' ||
    E'local currentRequests = redis.call(''ZCARD'', key)\n' ||
    E'if currentRequests &lt; limit then\n' ||
    E'    redis.call(''ZADD'', key, now, now)\n' ||
    E'    redis.call(''PEXPIRE'', key, window)\n' ||
    E'    return 1 -- Allowed\n' ||
    E'else\n' ||
    E'    return 0 -- Throttled (HTTP 429)\n' ||
    E'end\n' ||
    E'</code></pre>\n' ||
    E'<p>By executing the cleanup, count, and increment operations atomically inside a single Lua script execution, race conditions between parallel container instances are eliminated, ensuring strict adherence to API SLAs.</p>',
    NULL,
    'Neelastack Engineering',
    'Security',
    'Sliding Window Rate Limiting with Redis — Neelastack',
    'Protect APIs from brute-force and DoS attacks using sliding window rate limiting and atomic Redis Lua scripts in Spring Boot.',
    TRUE,
    now() - INTERVAL '18 hours'
),
(gen_random_uuid(),
    'The Transactional Outbox Pattern: Dual-Write Safety Without Distributed Transactions',
    'transactional-outbox-pattern-dual-write-safety',
    'Updating a database and publishing an external message cannot share an ACID transaction. How the Transactional Outbox pattern prevents ghost payments and dropped events.',
    E'<p>In modern backend engineering, applications frequently need to update a local database table (e.g., marking an invoice as paid) and notify an external system (e.g., publishing a message to a queue, triggering a webhook, or sending an onboarding email). Attempting to perform both operations sequentially inside a single Spring <code>@Transactional</code> method creates the classic <strong>dual-write consistency bug</strong>.</p>\n' ||
    E'<h2>The Dual-Write Failure Mode</h2>\n' ||
    E'<pre><code class="language-java">@Transactional\n' ||
    E'public void completePayment(UUID invoiceId) {\n' ||
    E'    invoice.setStatus(InvoiceStatus.PAID); // Write to PostgreSQL\n' ||
    E'    invoiceRepository.save(invoice);\n' ||
    E'    \n' ||
    E'    emailService.sendReceipt(invoice);     // External network call!\n' ||
    E'    // If the database crashes during commit, the receipt was sent for a payment that rolled back!\n' ||
    E'}\n' ||
    E'</code></pre>\n' ||
    E'<h2>The Solution: The Outbox Table</h2>\n' ||
    E'<p>Instead of communicating with external systems inside the database transaction, write an event payload to a dedicated <code>outbox_events</code> table within the <em>same local ACID transaction</em>:</p>\n' ||
    E'<pre><code class="language-sql">CREATE TABLE outbox_events (\n' ||
    E'    id UUID PRIMARY KEY,\n' ||
    E'    aggregate_type VARCHAR(64) NOT NULL,\n' ||
    E'    aggregate_id VARCHAR(64) NOT NULL,\n' ||
    E'    event_type VARCHAR(64) NOT NULL,\n' ||
    E'    payload JSONB NOT NULL,\n' ||
    E'    status VARCHAR(32) NOT NULL DEFAULT ''PENDING'',\n' ||
    E'    created_at TIMESTAMP WITH TIME ZONE NOT NULL\n' ||
    E');\n' ||
    E'</code></pre>\n' ||
    E'<p>A background worker (or Change Data Capture tool like Debezium) continuously polls pending outbox records using <code>SELECT ... FOR UPDATE SKIP LOCKED</code>, dispatches the external notifications, and marks events as <code>PROCESSED</code>. This guarantees <strong>at-least-once delivery</strong> with zero risk of database/messaging inconsistencies.</p>',
    NULL,
    'Neelastack Engineering',
    'Architecture',
    'Transactional Outbox Pattern in Spring Boot — Neelastack',
    'Eliminate dual-write bugs and guarantee reliable event delivery using the Transactional Outbox pattern with PostgreSQL and Spring Boot.',
    TRUE,
    now() - INTERVAL '6 hours'
);

-- =============================================================================
-- Taxonomy Association: Mapping Tags for Trending Posts
-- =============================================================================
INSERT INTO blog_post_tags (blog_post_id, tag)
SELECT blog_posts.id, seed_tags.tag
FROM (
    VALUES
        ('java-21-virtual-threads-spring-boot-benchmarks', 'java-21'),
        ('java-21-virtual-threads-spring-boot-benchmarks', 'spring-boot'),
        ('java-21-virtual-threads-spring-boot-benchmarks', 'concurrency'),
        ('java-21-virtual-threads-spring-boot-benchmarks', 'performance'),

        ('angular-signals-vs-rxjs-production-guide', 'angular'),
        ('angular-signals-vs-rxjs-production-guide', 'signals'),
        ('angular-signals-vs-rxjs-production-guide', 'rxjs'),
        ('angular-signals-vs-rxjs-production-guide', 'frontend'),

        ('postgresql-indexing-strategies-btree-brin-partial', 'postgresql'),
        ('postgresql-indexing-strategies-btree-brin-partial', 'database'),
        ('postgresql-indexing-strategies-btree-brin-partial', 'indexing'),
        ('postgresql-indexing-strategies-btree-brin-partial', 'performance'),

        ('distributed-tracing-spring-boot-opentelemetry', 'opentelemetry'),
        ('distributed-tracing-spring-boot-opentelemetry', 'observability'),
        ('distributed-tracing-spring-boot-opentelemetry', 'spring-boot'),
        ('distributed-tracing-spring-boot-opentelemetry', 'monitoring'),

        ('rate-limiting-rest-apis-sliding-window-redis', 'redis'),
        ('rate-limiting-rest-apis-sliding-window-redis', 'security'),
        ('rate-limiting-rest-apis-sliding-window-redis', 'api-design'),
        ('rate-limiting-rest-apis-sliding-window-redis', 'backend'),

        ('transactional-outbox-pattern-dual-write-safety', 'architecture'),
        ('transactional-outbox-pattern-dual-write-safety', 'distributed-systems'),
        ('transactional-outbox-pattern-dual-write-safety', 'spring-boot'),
        ('transactional-outbox-pattern-dual-write-safety', 'microservices')
) AS seed_tags(post_slug, tag)
JOIN blog_posts ON blog_posts.slug = seed_tags.post_slug;
