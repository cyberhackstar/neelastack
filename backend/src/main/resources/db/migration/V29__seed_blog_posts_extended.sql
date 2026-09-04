-- =============================================================================
-- Neelastack Platform: Expanded High-Authority Blog Seed Migration
-- Adds 7 in-depth, production-derived engineering articles targeting
-- high-intent enterprise search queries (Spring Boot, Angular SSR, Postgres, Security)
-- =============================================================================

INSERT INTO blog_posts
    (title, slug, excerpt, content, cover_image_url, author_name, category,
     meta_title, meta_description, published, published_at)
VALUES
(
    'Eliminating N+1 Queries in Spring Boot with JPA Projections and Batch Fetching',
    'spring-boot-jpa-eliminate-n-plus-one-queries',
    'The N+1 query pattern silently degrades API throughput under production concurrency. Here is how we enforce batch fetching, criteria join graphs, and interface-based DTO projections in Hibernate 6.',
    E'<p>In high-throughput enterprise systems, the single most common cause of latency spikes under database load is the <strong>N+1 query execution anti-pattern</strong>. When an application loads a collection of parent entities and subsequently triggers an individual SQL <code>SELECT</code> for each associated child record, database connection pools exhaust rapidly and p99 response times spike.</p>\n' ||
    E'<h2>The Root Cause: Default Lazy Fetch Traps</h2>\n' ||
    E'<p>While JPA specifications mandate <code>FetchType.LAZY</code> on <code>@OneToMany</code> relationships to prevent loading unbounded collections into memory, naive serialization through Jackson or accessing getter methods inside service layers triggers secondary queries inside active transaction boundaries:</p>\n' ||
    E'<pre><code class="language-sql">-- Initial Parent Query\n' ||
    E'SELECT id, client_name, status FROM quotations WHERE status = ''SENT'';\n\n' ||
    E'-- N Consecutive Sub-Queries (Triggered for every record in memory)\n' ||
    E'SELECT id, description, unit_price FROM quotation_line_items WHERE quotation_id = ''101'';\n' ||
    E'SELECT id, description, unit_price FROM quotation_line_items WHERE quotation_id = ''102'';\n' ||
    E'SELECT id, description, unit_price FROM quotation_line_items WHERE quotation_id = ''103'';\n' ||
    E'</code></pre>\n' ||
    E'<h2>Pattern 1: Interface-Driven Spring Data JPA Projections</h2>\n' ||
    E'<p>For read-only views, such as public quotations and sales summary dashboards, mapping directly into entity graphs introduces unnecessary persistence overhead. Instead, define strict read-only Spring Data projections that compile into single, flattened SQL queries:</p>\n' ||
    E'<pre><code class="language-java">public interface QuotationSummaryView {\n' ||
    E'    UUID getId();\n' ||
    E'    String getClientName();\n' ||
    E'    BigDecimal getTotalAmount();\n' ||
    E'    Instant getCreatedAt();\n' ||
    E'}\n\n' ||
    E'@Repository\n' ||
    E'public interface QuotationRepository extends JpaRepository&lt;Quotation, UUID&gt; {\n' ||
    E'    @Query("SELECT q.id as id, q.clientName as clientName, q.totalAmount as totalAmount, " +\n' ||
    E'           "q.createdAt as createdAt FROM Quotation q WHERE q.status = :status")\n' ||
    E'    List&lt;QuotationSummaryView&gt; findAllSummariesByStatus(@Param("status") QuotationStatus status);\n' ||
    E'}\n' ||
    E'</code></pre>\n' ||
    E'<h2>Pattern 2: Dynamic Entity Graphs for Complex Write Workflows</h2>\n' ||
    E'<p>When full entities must be retrieved for state mutations—such as recalculating milestone completion matrices or verifying multi-item line totals—apply JPA <code>@EntityGraph</code> annotations to execute explicit SQL <code>LEFT JOIN</code> fetches:</p>\n' ||
    E'<pre><code class="language-java">@EntityGraph(attributePaths = {"lineItems", "inquiry"})\n' ||
    E'@Query("SELECT q FROM Quotation q WHERE q.id = :id")\n' ||
    E'Optional&lt;Quotation&gt; findByIdWithLineItems(@Param("id") UUID id);\n' ||
    E'</code></pre>\n' ||
    E'<h2>Configuration-Level Safety Net: Global Batch Fetching</h2>\n' ||
    E'<p>For unavoidable lazy graph traversals, prevent single-row iterative querying by establishing global batch fetching within your <code>application.yml</code>:</p>\n' ||
    E'<pre><code class="language-yaml">spring:\n' ||
    E'  jpa:\n' ||
    E'    properties:\n' ||
    E'      hibernate:\n' ||
    E'        default_batch_fetch_size: 30\n' ||
    E'        order_inserts: true\n' ||
    E'        order_updates: true\n' ||
    E'</code></pre>\n' ||
    E'<p>This forces Hibernate to accumulate pending primary keys and execute parameterized <code>IN (?, ?, ?...)</code> batch clauses, consolidating hundreds of network trips into deterministic, microsecond database calls.</p>',
    NULL,
    'Neelastack Engineering',
    'Backend',
    'Spring Boot JPA: Eliminating N+1 Queries — Neelastack',
    'How to eliminate N+1 queries in Spring Boot 3 using JPA interface projections, Hibernate entity graphs, and batch fetch optimization.',
    TRUE,
    now() - INTERVAL '18 days'
),
(
    'Solving Hydration Mismatches in Angular SSR Applications',
    'solving-hydration-mismatches-angular-ssr',
    'Client-side hydration flickering and DOM state mismatches harm user experience and degrade Google Core Web Vitals. Here is how we build leak-free SSR pipelines in Angular.',
    E'<p>Angular Server-Side Rendering (SSR) allows single-page applications to deliver pre-rendered semantic HTML directly to crawlers and edge clients. However, when browser hydration executes, discrepancies between server-generated markup and client-side initialization cause <strong>hydration mismatch errors</strong>, leading to full page flickers, layout shifts, and wasted CPU cycles.</p>\n' ||
    E'<h2>The Anatomy of an Angular Hydration Mismatch</h2>\n' ||
    E'<p>When non-destructive hydration initializes, the client-side Angular engine inspects the pre-rendered DOM trees and matches browser state against server-rendered node IDs. Discrepancies emerge primarily when components access client-only globals directly during initial component construction:</p>\n' ||
    E'<ul>\n' ||
    E'<li>Reading browser storage (<code>localStorage</code>, <code>sessionStorage</code>) inside <code>ngOnInit()</code></li>\n' ||
    E'<li>Directly manipulating <code>window.innerWidth</code> or document objects during template compilation</li>\n' ||
    E'<li>Rendering non-deterministic output like unseeded <code>Math.random()</code> or unformatted local timezone timestamps</li>\n' ||
    E'</ul>\n' ||
    E'<h2>Pattern 1: Platform-Aware Lifecycle Guarding</h2>\n' ||
    E'<p>Never bind view templates directly to browser APIs. Instead, inject Angular''s <code>PLATFORM_ID</code> and use <code>isPlatformBrowser</code> / <code>isPlatformServer</code> to isolate client-specific side effects to post-hydration lifecycle hooks:</p>\n' ||
    E'<pre><code class="language-typescript">import { Component, OnInit, Inject, PLATFORM_ID, signal } from ''@angular/core'';\n' ||
    E'import { isPlatformBrowser } from ''@angular/common'';\n\n' ||
    E'@Component({\n' ||
    E'  selector: ''app-client-profile'',\n' ||
    E'  standalone: true,\n' ||
    E'  template: `\n' ||
    E'    &lt;div class="profile-card"&gt;\n' ||
    E'      @if (isClient()) {\n' ||
    E'        &lt;span class="status"&gt;Active Device: {{ deviceType() }}&lt;/span&gt;\n' ||
    E'      } @else {\n' ||
    E'        &lt;span class="status"&gt;Verifying session...&lt;/span&gt;\n' ||
    E'      }\n' ||
    E'    &lt;/div&gt;\n' ||
    E'  `\n' ||
    E'})\n' ||
    E'export class ClientProfileComponent implements OnInit {\n' ||
    E'  protected isClient = signal(false);\n' ||
    E'  protected deviceType = signal(''Desktop'');\n\n' ||
    E'  constructor(@Inject(PLATFORM_ID) private platformId: Object) {}\n\n' ||
    E'  ngOnInit(): void {\n' ||
    E'    if (isPlatformBrowser(this.platformId)) {\n' ||
    E'      this.isClient.set(true);\n' ||
    E'      this.deviceType.set(window.innerWidth &lt; 768 ? ''Mobile'' : ''Desktop'');\n' ||
    E'    }\n' ||
    E'  }\n' ||
    E'}\n' ||
    E'</code></pre>\n' ||
    E'<h2>Pattern 2: TransferState to Prevent Duplicate API Invocations</h2>\n' ||
    E'<p>Without a state hydration bridge, an Angular SSR application fetches data on the Node.js server to render the HTML, and then immediately triggers the exact same HTTP request from the browser during hydration. Use <code>TransferState</code> to package the server payload directly into the HTML:</p>\n' ||
    E'<pre><code class="language-typescript">const DATA_KEY = makeStateKey&lt;SolutionDto&gt;(''solution_data'');\n\n' ||
    E'if (this.transferState.hasKey(DATA_KEY)) {\n' ||
    E'  this.solution = this.transferState.get(DATA_KEY, null!);\n' ||
    E'  this.transferState.remove(DATA_KEY);\n' ||
    E'} else {\n' ||
    E'  this.solutionService.getBySlug(slug).subscribe(data =&gt; {\n' ||
    E'    this.solution = data;\n' ||
    E'    if (isPlatformServer(this.platformId)) {\n' ||
    E'      this.transferState.set(DATA_KEY, data);\n' ||
    E'    }\n' ||
    E'  });\n' ||
    E'}\n' ||
    E'</code></pre>\n' ||
    E'<p>Eliminating dual fetches guarantees a seamless 0ms hydration phase, driving Cumulative Layout Shift (CLS) scores to absolute zero.</p>',
    NULL,
    'Neelastack Engineering',
    'Frontend',
    'Angular SSR Hydration Mismatch Fixes — Neelastack',
    'Eliminate Angular Server-Side Rendering hydration mismatches and prevent duplicate API calls using TransferState and Platform Guards.',
    TRUE,
    now() - INTERVAL '12 days'
),
(
    'Zero-Downtime Blue-Green Deployments with Docker and Nginx',
    'zero-downtime-deployments-docker-nginx',
    'Deploying application upgrades should never drop active client requests. A guide to running atomic upstream rollouts with Docker Compose, health-check barriers, and Nginx reloads.',
    E'<p>In mission-critical enterprise platforms handling ongoing checkout flows and asynchronous webhooks, taking a web service offline during a maintenance window is unacceptable. Achieving true <strong>zero-downtime blue-green deployments</strong> does not require complex orchestrators like Kubernetes if you understand how to harness atomic reverse proxy swaps.</p>\n' ||
    E'<h2>The Deployment Dilemma</h2>\n' ||
    E'<p>A standard <code>docker compose down &amp;&amp; docker compose up -d</code> creates an unavoidable 5 to 30 second service outage while JVM environments start, Flyway migrations run, and JIT compilation completes. During this window, load balancers return HTTP 502 Bad Gateway responses to active users.</p>\n' ||
    E'<h2>The Architecture: Parallel Port Isolation</h2>\n' ||
    E'<p>We run two identical application slots (Blue on port 8081, Green on port 8082) fronted by a local Nginx reverse proxy using an upstream configuration file:</p>\n' ||
    E'<pre><code class="language-nginx"># /etc/nginx/conf.d/upstream.conf\n' ||
    E'upstream active_backend {\n' ||
    E'    server 127.0.0.1:8081;\n' ||
    E'}\n' ||
    E'</code></pre>\n' ||
    E'<h2>The Automated Promotion Script</h2>\n' ||
    E'<p>Our continuous delivery pipeline evaluates which slot is currently receiving traffic, launches the alternate slot with the new image, verifies the local healthcheck endpoint, and executes a hot reload:</p>\n' ||
    E'<pre><code class="language-bash">#!/usr/bin/env bash\n' ||
    E'set -euo pipefail\n\n' ||
    E'ACTIVE_PORT=$(grep -oE ''808[12]'' /etc/nginx/conf.d/upstream.conf)\n' ||
    E'TARGET_PORT=$([ "$ACTIVE_PORT" == "8081" ] &amp;&amp; echo "8082" || echo "8081")\n' ||
    E'TARGET_COLOR=$([ "$TARGET_PORT" == "8082" ] &amp;&amp; echo "green" || echo "blue")\n\n' ||
    E'echo "Deploying update to $TARGET_COLOR on port $TARGET_PORT..."\n' ||
    E'docker compose up -d "backend-$TARGET_COLOR"\n\n' ||
    E'# Enforce strict health check loop\n' ||
    E'for i in {1..30}; do\n' ||
    E'  if curl -s -f "http://127.0.0.1:$TARGET_PORT/api/v1/ping" | grep -q "PONG"; then\n' ||
    E'    echo "$TARGET_COLOR is fully healthy."\n' ||
    E'    break\n' ||
    E'  fi\n' ||
    E'  sleep 2\n' ||
    E'done\n\n' ||
    E'# Point Nginx upstream to the newly verified slot\n' ||
    E'sed -i "s/$ACTIVE_PORT/$TARGET_PORT/g" /etc/nginx/conf.d/upstream.conf\n' ||
    E'nginx -s reload\n\n' ||
    E'echo "Atomic traffic cutover complete. Draining old container..."\n' ||
    E'docker compose stop "$([ "$ACTIVE_PORT" == "8081" ] &amp;&amp; echo "backend-blue" || echo "backend-green")"\n' ||
    E'</code></pre>\n' ||
    E'<h2>Handling Database Schema Migrations Safely</h2>\n' ||
    E'<p>The critical requirement for zero-downtime architecture is <strong>backward-compatible database migrations</strong>. Flyway migrations must never rename columns or drop tables in a single step. We enforce the <em>Expand/Contract pattern</em>: introduce new nullable fields first, deploy code writing to both schemas, backfill historical records, and only drop deprecated structures in a subsequent deployment release.</p>',
    NULL,
    'Neelastack Engineering',
    'DevOps',
    'Zero-Downtime Blue-Green Deployments — Neelastack',
    'Master zero-downtime blue-green deployments using Docker Compose and Nginx hot reloads with automated health check cutovers.',
    TRUE,
    now() - INTERVAL '10 days'
),
(
    'Preventing Double Submissions with Distributed Locks and Redis',
    'preventing-double-submissions-redis-distributed-locks',
    'Network timeouts and repeated clicks can spawn duplicate credit card transactions or double bookings. Here is how we enforce distributed locks in Spring Boot using Redis.',
    E'<p>When building enterprise financial workflows, local JVM synchronization primitives (like Java''s <code>synchronized</code> keyword or <code>ReentrantLock</code>) fail completely once an application scales horizontally across multiple container replicas. To protect against concurrent invoice payments, double voucher redemptions, and race conditions, systems require <strong>distributed locking with strict acquisition timeouts</strong>.</p>\n' ||
    E'<h2>The Anatomy of a Concurrent Race Condition</h2>\n' ||
    E'<p>Consider a client double-clicking an invoice authorization button or two asynchronous webhooks firing within milliseconds of each other. Both application nodes inspect the database table, observe that the status is <code>PENDING</code>, and proceed to invoke upstream financial APIs simultaneously:</p>\n' ||
    E'<pre><code class="language-text">Thread A (Instance 1): SELECT status -&gt; PENDING\n' ||
    E'Thread B (Instance 2): SELECT status -&gt; PENDING\n' ||
    E'Thread A: Initiates Gateway Order Creation\n' ||
    E'Thread B: Initiates Gateway Order Creation (Duplicate!)\n' ||
    E'</code></pre>\n' ||
    E'<h2>Atomic Locking via Redis Lua Scripts</h2>\n' ||
    E'<p>A secure lock implementation must guarantee that lock acquisition, lease expiration, and lock release are strictly atomic. In Neelastack, we leverage atomic <code>SET resource_key token NX PX ttl</code> commands backed by validation tokens:</p>\n' ||
    E'<pre><code class="language-java">@Service\n' ||
    E'public class DistributedLockService {\n' ||
    E'    private final StringRedisTemplate redisTemplate;\n\n' ||
    E'    public boolean acquireLock(String lockKey, String lockToken, Duration leaseTime) {\n' ||
    E'        Boolean success = redisTemplate.opsForValue()\n' ||
    E'            .setIfAbsent(lockKey, lockToken, leaseTime);\n' ||
    E'        return Boolean.TRUE.equals(success);\n' ||
    E'    }\n\n' ||
    E'    public void releaseLock(String lockKey, String lockToken) {\n' ||
    E'        // Lua script ensures a client can only release its own lock\n' ||
    E'        String script = "if redis.call(''get'', KEYS[1]) == ARGV[1] then " +\n' ||
    E'                        "return redis.call(''del'', KEYS[1]) else return 0 end";\n' ||
    E'        redisTemplate.execute(new DefaultRedisScript&lt;&gt;(script, Long.class),\n' ||
    E'                              Collections.singletonList(lockKey),\n' ||
    E'                              lockToken);\n' ||
    E'    }\n' ||
    E'}\n' ||
    E'</code></pre>\n' ||
    E'<h2>Declarative Transaction Protection with Custom Aspect</h2>\n' ||
    E'<p>Rather than cluttering business domain services with infrastructure wiring, wrap mission-critical mutations with a declarative AOP interceptor:</p>\n' ||
    E'<pre><code class="language-java">@Transactional\n' ||
    E'@DistributedLock(key = "''invoice:pay:'' + #invoiceId", timeoutSeconds = 5)\n' ||
    E'public void processInvoicePayment(UUID invoiceId) {\n' ||
    E'    Invoice invoice = invoiceRepository.findById(invoiceId)\n' ||
    E'        .orElseThrow(() -&gt; new ResourceNotFoundException("Invoice not found"));\n' ||
    E'    if (invoice.getStatus() == InvoiceStatus.PAID) {\n' ||
    E'        return;\n' ||
    E'    }\n' ||
    E'    // Complete settlement logic safely\n' ||
    E'}\n' ||
    E'</code></pre>\n' ||
    E'<p>By isolating state mutations inside distributed lease windows, duplicate requests bounce off clean rate gates with zero risk of database corruption.</p>',
    NULL,
    'Neelastack Engineering',
    'Architecture',
    'Distributed Locks with Redis and Spring Boot — Neelastack',
    'How to prevent duplicate orders and concurrent write conflicts in distributed Spring Boot applications using Redis locks.',
    TRUE,
    now() - INTERVAL '8 days'
),
(
    'Structuring Enterprise Spring Boot Projects: Beyond the Standard 3-Tier Layering',
    'spring-boot-clean-modular-monolith-architecture',
    'The standard controller-service-repository hierarchy collapses as domains grow. Here is how we enforce vertical feature slicing and package-private domain boundaries.',
    E'<p>Almost every introductory Spring Boot tutorial presents an identical structural blueprint: <code>controllers/</code>, <code>services/</code>, and <code>repositories/</code> packages sitting at the root level. While sufficient for proof-of-concept projects, this technical layering rapidly degenerates into a tangled web of circular dependencies and blurred ownership in large enterprise environments.</p>\n' ||
    E'<h2>The Anti-Pattern of Technical Layering</h2>\n' ||
    E'<p>In a horizontally sliced codebase, every service method is declared <code>public</code> so controllers in another package can call them. Over time, developers import cross-boundary services arbitrarily. A customer registration service suddenly starts importing an invoice generation repository directly, breaking domain cohesion.</p>\n' ||
    E'<h2>The Solution: Feature-First Vertical Slicing</h2>\n' ||
    E'<p>Instead of grouping classes by technical responsibility, divide your application into <strong>business domains and bounded contexts</strong>:</p>\n' ||
    E'<pre><code class="language-text">com.neelastack\n' ||
    E'├── quotation\n' ||
    E'│   ├── internal\n' ||
    E'│   │   ├── QuotationEntity.java         (package-private)\n' ||
    E'│   │   ├── QuotationRepository.java     (package-private)\n' ||
    E'│   │   └── QuotationCalculator.java     (package-private)\n' ||
    E'│   ├── QuotationService.java            (public API interface)\n' ||
    E'│   └── QuotationDto.java                (public immutable DTO)\n' ||
    E'├── billing\n' ||
    E'│   ├── internal\n' ||
    E'│   └── InvoiceService.java\n' ||
    E'└── security\n' ||
    E'</code></pre>\n' ||
    E'<h2>Enforcing Architectural Boundaries with Java Package-Private Visibility</h2>\n' ||
    E'<p>Java''s default package-private visibility (no modifier) is one of the most underutilized access control features in modern software engineering. By keeping your JPA entities, repositories, and helper classes package-private inside <code>internal</code> packages, you make it physically impossible for code outside that module to access the database tables directly:</p>\n' ||
    E'<pre><code class="language-java">// Accessible only within com.neelastack.quotation.internal\n' ||
    E'@Entity\n' ||
    E'class Quotation {\n' ||
    E'    @Id\n' ||
    E'    private UUID id;\n' ||
    E'    private BigDecimal totalAmount;\n' ||
    E'}\n' ||
    E'</code></pre>\n' ||
    E'<p>Any external domain (such as Billing) must interact exclusively through a well-defined public service interface and strongly typed DTOs. This approach retains all the operational simplicity of a single deployable monolith while delivering the architectural hygiene and clean boundaries of microservices.</p>',
    NULL,
    'Neelastack Engineering',
    'Architecture',
    'Clean Modular Monolith Architecture in Spring Boot — Neelastack',
    'Architect clean Spring Boot applications using vertical slice architecture, package-private boundaries, and domain modularity.',
    TRUE,
    now() - INTERVAL '5 days'
),
(
    'Implementing TOTP Multi-Factor Authentication: RFC 6238 in Practice',
    'implementing-totp-mfa-rfc-6238-spring-boot',
    'Securing administrative control planes requires hardware-backed or software-based time OTP tokens. A step-by-step breakdown of implementing RFC 6238 TOTP with encrypted secret storage.',
    E'<p>Passwords alone are no longer sufficient to protect sensitive administrative actions, billing pipelines, and client data. Implementing <strong>Time-based One-Time Passwords (TOTP)</strong> based on the IETF RFC 6238 specification allows any mobile authenticator (Google Authenticator, Apple Passwords, 1Password) to serve as a reliable second factor.</p>\n' ||
    E'<h2>How the RFC 6238 Algorithm Computes Codes</h2>\n' ||
    E'<p>The TOTP algorithm is built on HMAC-SHA1. It takes a shared secret key $K$ and a moving factor derived from the current UNIX epoch timestamp divided into 30-second intervals:</p>\n' ||
    E'<pre><code class="language-text">Step 1: T = (Current UNIX Time - Initial Epoch 0) / 30\n' ||
    E'Step 2: Hash = HMAC-SHA1(Shared Secret K, T as 8-byte big-endian)\n' ||
    E'Step 3: Extract dynamic offset from lowest 4 bits of Hash\n' ||
    E'Step 4: Compute 6-digit verification code modulo 1,000,000\n' ||
    E'</code></pre>\n' ||
    E'<h2>Securing Shared Secrets at Rest</h2>\n' ||
    E'<p>The shared base32 secret key must never be saved in plain text in your database. If an unauthorized actor gets read access to your database backups, plain text secrets compromise the entire second factor. Always encrypt TOTP secrets using authenticated AES-256-GCM before writing to PostgreSQL:</p>\n' ||
    E'<pre><code class="language-java">@Service\n' ||
    E'public class TotpEncryptionService {\n' ||
    E'    private static final String ALGORITHM = "AES/GCM/NoPadding";\n' ||
    E'    private static final int GCM_TAG_LENGTH = 128;\n' ||
    E'    private static final int IV_LENGTH = 12;\n\n' ||
    E'    public String encryptSecret(String plainBase32Secret, SecretKey key) throws Exception {\n' ||
    E'        byte[] iv = new byte[IV_LENGTH];\n' ||
    E'        SecureRandom.getInstanceStrong().nextBytes(iv);\n' ||
    E'        Cipher cipher = Cipher.getInstance(ALGORITHM);\n' ||
    E'        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));\n' ||
    E'        byte[] cipherText = cipher.doFinal(plainBase32Secret.getBytes(StandardCharsets.UTF_8));\n\n' ||
    E'        // Prepend IV to ciphertext for storage\n' ||
    E'        ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);\n' ||
    E'        buffer.put(iv);\n' ||
    E'        buffer.put(cipherText);\n' ||
    E'        return Base64.getEncoder().encodeToString(buffer.array());\n' ||
    E'    }\n' ||
    E'}\n' ||
    E'</code></pre>\n' ||
    E'<h2>Allowing for Clock Drift Tolerances</h2>\n' ||
    E'<p>Mobile clocks drift relative to production NTP servers. When validating an incoming 6-digit code, always evaluate a sliding window of $\\pm 1$ time step (accounting for 30 seconds backward and 30 seconds forward). This completely eliminates false-negative verification errors while maintaining an airtight security boundary.</p>',
    NULL,
    'Neelastack Engineering',
    'Security',
    'Implementing TOTP RFC 6238 in Spring Boot — Neelastack',
    'A practical guide to implementing RFC 6238 TOTP two-factor authentication with AES-256-GCM encrypted database secrets in Spring Boot.',
    TRUE,
    now() - INTERVAL '2 days'
),
(
    'Building Search-Engine Friendly Internal Linking Networks in Modern SPAs',
    'building-search-engine-friendly-internal-linking-spa',
    'Search crawlers navigate the web through semantic link graphs, not client-side JavaScript clicks. How we structure programmatic topic clusters and contextual internal links.',
    E'<p>Single Page Applications frequently break the fundamental contract of the web: <strong>navigable, semantic hyperlinks</strong>. When frontend developers replace standard HTML anchor tags with <code>&lt;button (click)="navigateTo()"&gt;</code>, search bots can fail to discover deep content routes, leaving valuable pages unindexed.</p>\n' ||
    E'<h2>Why Googlebot Needs Pure HTML Links</h2>\n' ||
    E'<p>While search engine crawlers can execute JavaScript, parsing dynamic DOM interactions requires significantly more rendering budget. Google prioritizes static <code>&lt;a href="..."&gt;</code> tags when building its link index and calculating topical authority silos:</p>\n' ||
    E'<ul>\n' ||
    E'<li>Anchors without valid <code>href</code> attributes are skipped during discovery phases.</li>\n' ||
    E'<li>JavaScript-driven history state pushes without server-side hydration can result in orphaned URLs.</li>\n' ||
    E'<li>Fragment identifiers (<code>#section</code>) do not pass link equity across distinct entity topics.</li>\n' ||
    E'</ul>\n' ||
    E'<h2>Pattern: The Programmatic Topic Cluster Silo</h2>\n' ||
    E'<p>To build genuine domain authority around specialized engineering disciplines, pages must cross-link contextually within defined thematic silos. In Neelastack, our solutions, case studies, and engineering journal articles cross-reference each other through explicit database relationships:</p>\n' ||
    E'<pre><code class="language-text">                 [ Core Service: Backend Modernization ]\n' ||
    E'                                   │\n' ||
    E'         ┌─────────────────────────┴─────────────────────────┐\n' ||
    E'         ▼                                                   ▼\n' ||
    E' [ Solution: Spring Boot Migration ]             [ Solution: PostgreSQL Optimization ]\n' ||
    E'         │                                                   │\n' ||
    E'         ▼                                                   ▼\n' ||
    E' [ Case Study: ElectroMart ]                   [ Deep Technical Guide: N+1 Queries ]\n' ||
    E'</code></pre>\n' ||
    E'<h2>Semantic Angular Template Implementation</h2>\n' ||
    E'<p>Always combine Angular''s <code>routerLink</code> with full semantic HTML anchor elements and descriptive, contextual anchor text:</p>\n' ||
    E'<pre><code class="language-html">&lt;!-- BAD: Opaque to search engine discovery --&gt;\n' ||
    E'&lt;button (click)="openProject(''electromart'')"&gt;View Project&lt;/button&gt;\n\n' ||
    E'&lt;!-- GOOD: Crawlable, semantic, anchor-rich link equity --&gt;\n' ||
    E'&lt;a [routerLink]="[''/portfolio'', project.slug]" class="internal-link"&gt;\n' ||
    E'  Read our full architecture teardown on &lt;strong&gt;{{ project.title }}&lt;/strong&gt;\n' ||
    E'&lt;/a&gt;\n' ||
    E'</code></pre>\n' ||
    E'<p>This guarantees that search engine bots effortlessly traverse your technical authority network, distributing page rank from high-traffic landing pages straight into your core conversion funnels.</p>',
    NULL,
    'Neelastack Engineering',
    'SEO',
    'Crawlable Internal Linking for SPAs — Neelastack',
    'How to build semantic, search-engine-friendly internal linking structures and topic clusters in Angular single-page applications.',
    TRUE,
    now() - INTERVAL '6 hours'
);

-- =============================================================================
-- Taxonomy Association: Mapping Tags for Extended Posts
-- =============================================================================
INSERT INTO blog_post_tags (blog_post_id, tag)
SELECT blog_posts.id, seed_tags.tag
FROM (
    VALUES
        ('spring-boot-jpa-eliminate-n-plus-one-queries', 'spring-boot'),
        ('spring-boot-jpa-eliminate-n-plus-one-queries', 'jpa'),
        ('spring-boot-jpa-eliminate-n-plus-one-queries', 'postgresql'),
        ('spring-boot-jpa-eliminate-n-plus-one-queries', 'performance'),

        ('solving-hydration-mismatches-angular-ssr', 'angular'),
        ('solving-hydration-mismatches-angular-ssr', 'ssr'),
        ('solving-hydration-mismatches-angular-ssr', 'web-vitals'),
        ('solving-hydration-mismatches-angular-ssr', 'frontend'),

        ('zero-downtime-deployments-docker-nginx', 'devops'),
        ('zero-downtime-deployments-docker-nginx', 'docker'),
        ('zero-downtime-deployments-docker-nginx', 'nginx'),
        ('zero-downtime-deployments-docker-nginx', 'ci-cd'),

        ('preventing-double-submissions-redis-distributed-locks', 'redis'),
        ('preventing-double-submissions-redis-distributed-locks', 'concurrency'),
        ('preventing-double-submissions-redis-distributed-locks', 'architecture'),
        ('preventing-double-submissions-redis-distributed-locks', 'backend'),

        ('spring-boot-clean-modular-monolith-architecture', 'spring-boot'),
        ('spring-boot-clean-modular-monolith-architecture', 'clean-architecture'),
        ('spring-boot-clean-modular-monolith-architecture', 'design-patterns'),

        ('implementing-totp-mfa-rfc-6238-spring-boot', 'security'),
        ('implementing-totp-mfa-rfc-6238-spring-boot', 'auth'),
        ('implementing-totp-mfa-rfc-6238-spring-boot', 'mfa'),
        ('implementing-totp-mfa-rfc-6238-spring-boot', 'cryptography'),

        ('building-search-engine-friendly-internal-linking-spa', 'seo'),
        ('building-search-engine-friendly-internal-linking-spa', 'angular'),
        ('building-search-engine-friendly-internal-linking-spa', 'content-strategy')
) AS seed_tags(post_slug, tag)
JOIN blog_posts ON blog_posts.slug = seed_tags.post_slug;
