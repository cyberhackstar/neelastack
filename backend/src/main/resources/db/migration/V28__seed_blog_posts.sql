-- Neelastack platform - starter blog content
-- Seeds a handful of published posts so /blog isn't empty on a fresh install.
-- Safe to edit or delete afterwards via the admin blog CMS (/admin/content/blog).

INSERT INTO blog_posts
    (title, slug, excerpt, content, cover_image_url, author_name, category,
     meta_title, meta_description, published, published_at)
VALUES
(
    'Why We Chose a Modular Monolith Over Microservices',
    'modular-monolith-over-microservices',
    'Microservices are the default advice for "serious" architecture. For a team our size, a well-structured monolith ships faster and breaks less.',
    E'<p>Every architecture conversation eventually turns to microservices, as if splitting a codebase into a dozen deployable services is a rite of passage. For most teams under a certain size, it is the wrong call.</p>\n<p>A modular monolith gives you clear internal boundaries — separate packages, separate concerns, enforced dependency direction — without the operational tax of a dozen deployments, a service mesh, and distributed tracing just to answer "why is this request slow."</p>\n<h2>What we actually optimized for</h2>\n<p>Deployment simplicity, transactional integrity across related writes (a quotation acceptance touching inquiries, engagements, and audit logs in one transaction), and the ability for a small team to reason about the whole system.</p>\n<h2>When we would reconsider</h2>\n<p>If a single subsystem needed independent scaling, a different runtime, or ownership by a separate team, that is the point to carve it out — not before.</p>',
    NULL,
    'Neelastack Engineering',
    'Architecture',
    'Modular Monolith vs Microservices — Neelastack',
    'Why a modular monolith beat microservices for our team size, and the signals that would change that decision.',
    TRUE,
    now() - INTERVAL '21 days'
),
(
    'Designing an Idempotent Payment Webhook Handler',
    'idempotent-payment-webhook-handler',
    'Payment gateways retry webhooks. If your handler is not idempotent, a network blip can charge — or credit — a client twice.',
    E'<p>Razorpay, like most payment providers, retries webhook delivery on timeout or a non-2xx response. That means your endpoint will, eventually, receive the same event more than once — and your job is to make sure processing it twice has the same effect as processing it once.</p>\n<h2>The pattern</h2>\n<p>Store every inbound webhook event keyed by the provider''s event ID before doing any business logic. Use a unique constraint on that ID. If the insert fails because the row already exists, you have seen this event before — acknowledge it and stop.</p>\n<h2>What this protects against</h2>\n<p>A slow database write causing a timeout, the provider retrying, and your system applying a payment confirmation twice — silently double-crediting an invoice.</p>\n<h2>Beyond dedup</h2>\n<p>Track attempt counts and last-seen timestamps on the same table. It turns "did this webhook ever arrive" from a support mystery into a one-row lookup.</p>',
    NULL,
    'Neelastack Engineering',
    'Backend',
    'Idempotent Payment Webhooks — Neelastack',
    'A practical pattern for handling retried payment webhooks without double-processing a payment.',
    TRUE,
    now() - INTERVAL '14 days'
),
(
    'Refresh Token Rotation, Explained Without the Jargon',
    'refresh-token-rotation-explained',
    'Rotating refresh tokens on every use, and revoking the whole session family on replay, closes a quiet but serious hole in JWT auth.',
    E'<p>A long-lived refresh token that never changes is a single secret that, if leaked, grants indefinite access. Rotation fixes that by treating every refresh token as single-use.</p>\n<h2>How it works</h2>\n<p>Each time a client exchanges a refresh token for a new access token, the server also issues a new refresh token and immediately invalidates the old one. Tokens belong to a "session family" — a chain that traces back to the original login.</p>\n<h2>The important part: replay detection</h2>\n<p>If an already-used (rotated-out) refresh token is presented again, that is a strong signal the token was copied by an attacker who is now racing the legitimate client. The correct response is not to quietly reject that one request — it is to revoke the entire session family and force a fresh login on every device using it.</p>\n<h2>Why bother</h2>\n<p>It turns a leaked refresh token from "silent indefinite access" into "one usable request, then a tripped alarm."</p>',
    NULL,
    'Neelastack Engineering',
    'Security',
    'Refresh Token Rotation Explained — Neelastack',
    'How refresh token rotation and session-family revocation protect against leaked tokens.',
    TRUE,
    now() - INTERVAL '7 days'
),
(
    'From Lead to Invoice: Mapping a B2B Revenue Pipeline',
    'lead-to-invoice-revenue-pipeline',
    'Most "contact us" forms end at an email in someone''s inbox. Here is what it looks like to model the entire lead-to-revenue lifecycle instead.',
    E'<p>A contact form that emails a founder is fine for a hobby project. Once there is a sales process — qualification, quoting, follow-up, conversion, delivery, invoicing — that process deserves to be modeled as data, not tribal knowledge.</p>\n<h2>The stages we track</h2>\n<p>Inquiry, lead score, first contact, quotation sent, quotation viewed, quotation accepted or declined, client conversion, engagement/project, milestones, invoicing, payment, and reconciliation. Each transition is timestamped and attributable.</p>\n<h2>Why this matters beyond reporting</h2>\n<p>Attribution data (which channel, which campaign, which landing page produced a lead that became revenue) turns marketing spend from a guess into a measurable input. Follow-up tracking prevents warm leads from going cold because nobody remembered to reply.</p>\n<h2>The tradeoff</h2>\n<p>This is more schema and more workflow than a simple form. It only pays off once inquiry volume is high enough that "just remember to follow up" stops being reliable — but past that point, it changes how a business operates.</p>',
    NULL,
    'Neelastack Engineering',
    'Product',
    'Lead-to-Invoice Revenue Pipeline — Neelastack',
    'How we modeled a full B2B lead-to-revenue lifecycle instead of a contact form that just sends an email.',
    TRUE,
    now() - INTERVAL '3 days'
),
(
    'SSR and SEO for Angular: Getting 404s Right',
    'angular-ssr-seo-404s',
    'A dynamic route that renders "not found" content but still returns HTTP 200 quietly teaches Google that the page exists. Here is the fix.',
    E'<p>Server-side rendering solves the classic SPA SEO problem — an empty <code>&lt;div id="app"&gt;</code> in the initial HTML — by rendering real content on the server. But it introduces a subtler bug if you are not careful: a dynamic route (a blog post or portfolio item by slug) that fails to find a record can still render a "not found" component and return an HTTP 200 status.</p>\n<h2>Why that is a problem</h2>\n<p>Search engines treat the status code as authoritative. A 200 response tells a crawler "this URL is a real, indexable page" even if the visible content says otherwise. Left unfixed, you can end up with thin or empty pages sitting in the index.</p>\n<h2>The fix</h2>\n<p>When a server-rendered route cannot resolve its data, set the response status to 404 explicitly (and consider a <code>noindex</code> meta tag as a second layer of defense) before rendering the not-found view — rather than letting the default 200 slip through.</p>\n<h2>Test it directly</h2>\n<p>Do not trust the browser here — check the actual response status with a plain HTTP request against a known-bad slug and confirm it comes back as 404.</p>',
    NULL,
    'Neelastack Engineering',
    'SEO',
    'Angular SSR: Getting 404s Right — Neelastack',
    'Why a server-rendered "not found" page needs a real 404 status code, and how a silent 200 hurts SEO.',
    TRUE,
    now() - INTERVAL '1 days'
);

INSERT INTO blog_post_tags (blog_post_id, tag)
SELECT blog_posts.id, seed_tags.tag
FROM (
    VALUES
        ('modular-monolith-over-microservices', 'architecture'),
        ('modular-monolith-over-microservices', 'spring-boot'),
        ('idempotent-payment-webhook-handler', 'payments'),
        ('idempotent-payment-webhook-handler', 'backend'),
        ('idempotent-payment-webhook-handler', 'razorpay'),
        ('refresh-token-rotation-explained', 'security'),
        ('refresh-token-rotation-explained', 'auth'),
        ('lead-to-invoice-revenue-pipeline', 'product'),
        ('lead-to-invoice-revenue-pipeline', 'sales'),
        ('angular-ssr-seo-404s', 'seo'),
        ('angular-ssr-seo-404s', 'angular')
) AS seed_tags(post_slug, tag)
JOIN blog_posts ON blog_posts.slug = seed_tags.post_slug;
