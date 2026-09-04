# Neelastack — Full Build

Enterprise-grade client-acquisition and delivery platform for an independent software engineering practice. Spring Boot 3.4 (Java 21) + Angular 19 (SSR) + PostgreSQL + Redis.

See [`docs/FEATURES.md`](docs/FEATURES.md) for a full feature-by-feature breakdown of
what the platform does (public site, lead qualification, quotations, client portal,
payments, admin back office, security/MFA, and infrastructure).

## Structure
- `backend/` — Spring Boot API (JWT auth, Postgres, Flyway, Redis, Swagger)
- `frontend/` — Angular SSR app
- `infra/nginx/` — reverse proxy config for production (see `docs/DEPLOY-ORACLE-CLOUDFLARE.md`:
  public TLS is terminated by Cloudflare, not by this container — nginx listens on plain
  HTTP behind a Cloudflare Tunnel)
- `.github/workflows/ci-cd.yml` — build, test, Docker build/push to GHCR, deploy to Oracle VM
- `docker-compose.yml` — local dev stack
- `docker-compose.prod.yml` — production stack (used on the Oracle VM)

## Run locally
```bash
cp .env.example .env      # edit values
docker compose up --build
```
- Backend: http://localhost:8080  (Swagger: /swagger-ui.html)
- Frontend: http://localhost:4000
- No admin account is seeded by the migrations (the old permanently-committed
  `admin@neelastack.com` / `ChangeMe@123` fixture was removed in migration `V24` — a
  published credential in version control was never actually safe). Instead, set
  `ADMIN_BOOTSTRAP_EMAIL` and `ADMIN_BOOTSTRAP_PASSWORD` (12+ characters) in your `.env`
  before first boot; `AdminBootstrapRunner` provisions exactly one admin from those values
  on first startup only, and forces a password change (and MFA enrollment) before that
  account can do anything else. `ADMIN_BOOTSTRAP_FULL_NAME` is optional.

## Angular version note
Built on Angular 19. As of writing, Angular 21 is the current LTS release and Angular 22 is
active — 19 is no longer supported upstream, so a controlled upgrade is due. Same architecture
either way; upgrading is a version bump, not a rewrite, but run the full test suite (including
E2E) before and after to catch breaking changes in the CLI/build pipeline and RxJS interop.

## GitHub Actions secrets needed for deploy
`DEPLOY_HOST`, `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS` (pinned host key(s) for `DEPLOY_HOST`,
obtained once via a trusted channel — see the `deploy` job in `.github/workflows/ci-cd.yml`
for exactly how these are used). The production `.env` itself (DB/Redis/JWT secrets,
`ADMIN_BOOTSTRAP_*`, Razorpay, Cloudinary, Google OAuth, SMTP) lives on the Oracle VM at
`/opt/neelastack/.env` and is not passed through GitHub Actions.

## GitHub Actions secrets needed for CI to pass
`NVD_API_KEY` — **required**, not optional, despite the name suggesting otherwise. The
`dependency-scan-backend` job's OWASP Dependency-Check step fails outright (before it even
produces a report) if this is unset, because NVD aggressively rate-limits unauthenticated
feed updates. Register a free key at
[nvd.nist.gov/developers/request-an-api-key](https://nvd.nist.gov/developers/request-an-api-key)
and add it under **Settings → Secrets and variables → Actions**.

---

## Phase 1 — Foundation
Auth (register/login/refresh, JWT, bcrypt, RBAC), DB schema + migrations, Docker for both apps,
Nginx + SSL + Certbot, CI/CD pipeline, SSR-ready frontend shell.

## Phase 2 — Public site & SEO
- Visual identity: dark systems-dashboard theme (Space Grotesk / IBM Plex Sans / JetBrains Mono),
  amber + teal accents, CSS 3D tilt cards, animated SVG architecture diagram in the hero.
- Public pages: Home, Services, Portfolio (list + case study detail), Blog (list + article detail), About, Contact.
- Backend: `Service`, `Project`, `BlogPost` entities with public read + admin (ROLE_ADMIN) write
  endpoints under `/api/v1/admin/**` — this is your CMS.
- SEO: per-route meta tags + canonical URLs + Open Graph/Twitter cards (`SeoService`), JSON-LD
  structured data, server-generated `/sitemap.xml` and `/robots.txt` that stay in sync with
  published content automatically.

**On "3D effects":** used CSS 3D transforms (tilt-on-hover cards, layered depth, the browser-mockup
frames added later) instead of Three.js/WebGL — a full 3D engine would hurt load time and Core Web
Vitals, working directly against the SEO goal.

```bash
curl http://localhost:8080/api/v1/public/services
curl http://localhost:8080/api/v1/public/projects
curl http://localhost:8080/api/v1/public/blog
```
Admin writes need a bearer token from `/api/v1/auth/login`:
```bash
curl -X POST http://localhost:8080/api/v1/admin/projects \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"title":"Example","slug":"example","summary":"...","techStack":["Spring Boot","Angular"],"featured":true,"published":true}'
```

## Phase 3 — Lead capture & quotation workflow
- Lead capture: `/contact` posts to `/api/v1/public/inquiries`.
- Email notifications: on submit, the lead gets a confirmation and you get an admin alert (both
  async, both fail silently into logs if SMTP isn't configured — the request never fails because
  of a mail error).
- Quotation workflow: `/admin/inquiries` lists every lead. Click into one to update status (NEW →
  CONTACTED → QUOTED → WON/LOST), build a quotation with line items, and send it — which emails
  the client and auto-marks the inquiry QUOTED.
- SMTP setup: set `MAIL_USERNAME`/`MAIL_PASSWORD` in `.env` to a Gmail **app password** (enable
  2FA first, regular password won't work) or swap in SendGrid/Mailgun/SES SMTP.

## Phase 4 — Client dashboard & file sharing
- Any logged-in user sees **My Projects** in the navbar → `/dashboard` lists their engagements,
  `/dashboard/:id` shows milestones and files. Admins get edit controls on any engagement; clients
  see a read-only view of their own.
- File sharing via Cloudinary: uploads go through the backend (10MB limit, PDFs/images/Office
  docs/zip/text only), never direct browser-to-Cloudinary, so nothing bypasses auth.
- Converting a lead: on `/admin/inquiries/:id`, "Start a client project" creates an `Engagement`
  linked to that inquiry's email. **The client must already have a registered account** at that
  email — engagements link to a `User`, not a bare address.
- Cloudinary setup: free account at cloudinary.com, set `CLOUDINARY_CLOUD_NAME`,
  `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` in `.env`.

**Trying it end-to-end:** register a second ("client") account → as admin, "Start a client
project" on a lead using that email → log in as the client → `/dashboard` shows the project.

## Phase 5 — Payments & invoicing
- Admins create invoices on `/dashboard/:id`. Clients click "Pay now" → Razorpay Checkout opens →
  on success, the payment ID + signature are verified server-side before the invoice flips to PAID
  — never trusted from the browser alone.
- Reconciliation backstop: a webhook at `/api/v1/payments/webhook` (authenticated by Razorpay's own
  HMAC signature) marks invoices PAID from Razorpay's server-to-server event too, so a payment
  still reconciles even if the client's browser closes mid-checkout.
- Razorpay setup: test mode at razorpay.com, put the Key ID/Secret in `.env` as `RAZORPAY_KEY_ID`
  / `RAZORPAY_KEY_SECRET`. For the webhook: Settings → Webhooks → point at
  `https://neelastack.com/api/v1/payments/webhook`, subscribe to `payment.captured`, put the
  secret in `.env` as `RAZORPAY_WEBHOOK_SECRET`. Test card: `4111 1111 1111 1111`, any future
  expiry, any CVV.

## Phase 6 — Hardening & admin analytics
- Redis caching on public services/projects/blog-post-by-slug (15 min TTL, auto-evicted on admin
  edits). Fails *open*: a `CacheErrorHandler` catches any Redis failure (connection refused,
  timeout, serialization error), logs a warning, and lets the request fall through to the database
  — a Redis outage degrades performance, it never returns a 500.
- Rate limiting: login (10/min), register (5/10min), contact form (5/10min), throttled per IP via
  Redis. Also fails *open* if Redis is unreachable — a Redis outage never takes the site down.
- Sentry error tracking wired in but inert until you set `SENTRY_DSN` in `.env` (free at sentry.io).
- Admin dashboard at `/admin`: inquiry counts, active projects, revenue collected vs. pending,
  status breakdown, recent leads.

**Running the backend standalone (not via `docker compose`)?** You still need Postgres *and*
Redis reachable — e.g. `docker run -p 6379:6379 redis:7-alpine` alongside your Postgres instance.
Without the fail-open handling above this used to 500 on every public content endpoint if Redis
wasn't running; now it just logs a warning and serves uncached.

---

## Content & design polish pass
- **Logo**: a custom isometric-cube mark (amber/teal/dark-teal faces) plus wordmark — navbar,
  footer, and SVG favicon (`frontend/src/assets/favicon.svg`).
- **Dependency audit**: cross-checked every Java import against `pom.xml` and every TS import
  against `package.json`. Fixed one real gap — `org.json` needed an explicit dependency since
  razorpay-java's transitive version isn't reliable to build against.
- **About page**: rewritten with your real background — LTIMindtree, GymAI, the car rental
  platform, MCA/BCA, OCJP certification — plus a "how I work" process section. `Person` JSON-LD.
- **Team page** (new, `/team`): you, Padmasinha Chitte, and Anuragdeep Srivastav as collaborators,
  honestly. **No fabricated team members** — presenting fake people as staff is misrepresentation
  to clients evaluating who they're hiring. Edit `team.component.ts` to correct their roles/bios;
  I used neutral placeholders since I don't know their actual specialties.
- **Portfolio**: 4 real projects (V6 migration) — ElectroMart (pulled real detail from the live
  site), Ladies Apparel, GymAI, and the car rental platform, replacing the previously empty
  portfolio. Add more anytime via the admin API.
- **3D browser-mockup cards**: projects render inside a tilted, shadowed browser-chrome frame (CSS
  3D transforms, not WebGL). Shows a real screenshot once you set a project's `coverImageUrl` via
  the CMS API; falls back to a styled placeholder with the project's initials until then.
- **Services page**: richer per-service descriptions (V7 migration), a guarantees section, and an
  FAQ section with `FAQPage` schema markup — Google can surface this as expandable rich snippets.
- **Contact page**: two-column layout — form plus a "what happens next" sidebar and direct-email
  fallback.
- **SEO additions**: `BreadcrumbList` on case-study pages, `Person` on About, `FAQPage` on
  Services, on top of the `Organization`/`Article` schema already in place.

**For real screenshots:** the browser-mockup cards look best with actual screenshots. Take one of
each live project, upload it anywhere, and set `coverImageUrl` via
`PUT /api/v1/admin/projects/{id}` — no code change needed.

**What I didn't add:** fabricated testimonials, client logos, or stats like "50+ clients served" —
none of that exists yet, and inventing it is the kind of thing that damages trust if a client ever
checks. Worth adding for real once you have 2-3 genuine quotes.

---

## Mobile responsiveness & navbar (latest pass)
- **Mobile navigation menu**: the navbar previously just hid all its links below 780px with no
  way to reach them. There's now a proper hamburger menu — slide-in panel, backdrop, Escape-to-close,
  body-scroll lock while open, auto-closes on navigation.
- **Responsive audit across every page**: fixed unhandled two/three-column grids on narrow screens
  in the admin inquiry detail page, the client dashboard's milestone/invoice forms, and the admin
  analytics bar chart. Wrapped every admin data table (services/projects/blog CMS, inquiries list)
  in a horizontally-scrolling container instead of letting them overflow.

## Security & correctness audit fixes
A thorough audit surfaced real bugs — all fixed:
- **Missing `package-lock.json`**: CI and Docker both run `npm ci`, which hard-fails without a
  committed lockfile. Generated a real one (also incidentally confirms every dependency specified
  throughout this build actually resolves).
- **Production API URL mismatch**: the frontend pointed at `https://api.neelastack.com`, a
  subdomain the supplied Nginx config never configures. Fixed to use a same-origin relative path
  (`/api/v1`) matching the actual reverse-proxy setup — with a dedicated SSR-only interceptor so
  Angular Universal's server-side rendering (which can't resolve relative URLs the way a browser
  can) reaches the backend directly over the internal Docker network instead.
- **CI silently swallowed test failures** (`|| true`). Fixed to skip cleanly when no `.spec.ts`
  files exist yet (none do) and to genuinely enforce results the moment real tests are added.
- **Private pages had no `noindex`**: quotation links, the client dashboard, and all `/admin/**`
  pages could theoretically be indexed if a URL ever leaked. Every private/utility page now sends
  `noindex, nofollow`.
- **Refresh endpoint didn't check token type** — a still-valid 15-minute access token could be
  used interchangeably with a refresh token. Now explicitly rejected.
- **Malformed/expired JWTs could crash a request** with a raw 500 instead of failing cleanly to
  "anonymous" — fixed in both the refresh endpoint and the main auth filter.
- **Cross-tenant file deletion gap**: deleting a project file only checked that the caller could
  access *some* engagement, never that the file actually belonged to *that* engagement. Fixed.
- **File type validation trusted the client-supplied header** (trivially spoofable). Now sniffs
  actual file bytes via Apache Tika.
- **Cloudinary files were fully public** — anyone with a URL, including people who never logged
  into the site, could access a client's files indefinitely. Switched to Cloudinary's
  `authenticated` delivery type with signed URLs regenerated fresh on every request.
- **Soft-404**: unknown URLs redirected straight to the homepage (a pattern Google explicitly
  flags). Replaced with a real "page not found" view.
- **Invoice-number race condition**: two concurrent invoice creations could compute the same
  count-based number before either committed. Now retries with a fresh number on conflict instead
  of throwing a raw constraint-violation error.
- **`og-default.png` was referenced but never created.** Pointed at the existing SVG logo as a
  stopgap — note that most platforms (Facebook, LinkedIn, Slack) don't render SVG for link
  previews, so a real 1200×630 branded PNG is still worth adding.

**One thing I couldn't fully verify:** the Cloudinary signed-URL generation (`FileStorageService.generateSignedUrl`)
uses `cloudinary.url().resourceType(...).type("authenticated").signed(true)` — I'm reasonably
confident in this API shape but have no compiler in this environment to confirm it. If it doesn't
compile, the error will be immediate and easy to fix (see the pattern from earlier `CacheConfig`
fixes in this same README).

**Not done — P2, genuinely fine to defer:** Postgres full-text search for blog (current `LIKE`-based
search is fine at dozens-to-low-hundreds of posts), sitemap chunking (not needed until you have
thousands of URLs).

## On the content/positioning review
A separate strategic review suggested ~27 additions (industry-specific service pages, a project
estimator, an interactive architecture explorer, a technology radar, live system status, topic-
cluster SEO content, etc). Those are good ideas worth working through over time, but I didn't build
them blind in this pass — most need real inputs only you can provide (actual case-study metrics,
real client industries, genuine uptime data) or are substantial standalone features better scoped
deliberately rather than bolted on. Two content-strategy points from that review are worth acting
on directly: reframing copy around business outcomes ("a system that doesn't break") rather than
technology lists, and building out the real case-study depth (problem → approach → architecture →
outcome) for each portfolio project beyond what's there now. Both are copy-writing work best done
with your input on what's actually true for each project, not fabricated by me.
