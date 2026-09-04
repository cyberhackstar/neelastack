import { RenderMode, ServerRoute } from '@angular/ssr';

/**
 * Route-level rendering strategy (Angular 19 hybrid rendering / "server routing").
 * Three modes, chosen per route by what that route actually needs:
 *
 * - Prerender (SSG): build-time HTML for routes with zero per-request/DB dependency.
 *   Near-instant TTFB, 100/100-friendly Lighthouse scores, and Googlebot gets fully
 *   rendered HTML with no server round-trip at all. Content only refreshes on the
 *   next deploy, which is why this is reserved for pages that don't read from the
 *   CMS — the moment a route depends on live services/projects/blog data, a stale
 *   prerendered snapshot would silently show outdated info until the next build.
 * - Server (SSR): rendered per-request, always fresh. Used for every route backed
 *   by the database (home's featured projects, services, portfolio, blog) and for
 *   the token-scoped client quote page. Pair this with a short-TTL, stale-while-
 *   revalidate cache at the nginx/CDN layer (see infra/nginx) to get most of
 *   prerendering's speed without the staleness risk — that's the practical
 *   equivalent of ISR for an Angular app today.
 * - Client (CSR-only): skips SSR entirely. Used for authenticated app-shell routes
 *   (dashboard, admin) and the auth flow screens — all already `noindex` via
 *   SeoService, so there's no crawling benefit to rendering them server-side, only
 *   server load to avoid.
 *
 * NOTE ON FRESHNESS: publishing new content re-triggers an IndexNow ping
 * (see IndexNowService) so Bing/Yandex/Seznam pick it up quickly regardless of
 * render mode — that's independent of this file and doesn't require a rebuild.
 */
export const serverRoutes: ServerRoute[] = [
  // ---- Prerendered at build time: no DB dependency ----
  { path: 'about', renderMode: RenderMode.Prerender },
  { path: 'team', renderMode: RenderMode.Prerender },
  { path: 'contact', renderMode: RenderMode.Prerender },
  { path: 'estimate', renderMode: RenderMode.Prerender },
  { path: 'architecture-review', renderMode: RenderMode.Prerender },
  { path: 'audit-preview', renderMode: RenderMode.Prerender },

  // ---- Server-rendered per request: reads live CMS/DB data ----
  { path: '', renderMode: RenderMode.Server },
  { path: 'services', renderMode: RenderMode.Server },
  { path: 'portfolio', renderMode: RenderMode.Server },
  { path: 'portfolio/:slug', renderMode: RenderMode.Server },
  { path: 'blog', renderMode: RenderMode.Server },
  { path: 'blog/:slug', renderMode: RenderMode.Server },
  { path: 'solutions', renderMode: RenderMode.Server },
  { path: 'solutions/:slug', renderMode: RenderMode.Server },
  { path: 'quote/:token', renderMode: RenderMode.Server },
  { path: 'testimonial/:token', renderMode: RenderMode.Server },

  // ---- Client-only: authenticated app shell + auth flow, all noindex already ----
  { path: 'login', renderMode: RenderMode.Client },
  { path: 'register', renderMode: RenderMode.Client },
  { path: 'forgot-password', renderMode: RenderMode.Client },
  { path: 'reset-password', renderMode: RenderMode.Client },
  { path: 'verify-email', renderMode: RenderMode.Client },
  { path: 'oauth-callback', renderMode: RenderMode.Client },
  { path: 'dashboard', renderMode: RenderMode.Client },
  { path: 'dashboard/:id', renderMode: RenderMode.Client },
  { path: 'admin', renderMode: RenderMode.Client },
  { path: 'admin/content/services', renderMode: RenderMode.Client },
  { path: 'admin/content/projects', renderMode: RenderMode.Client },
  { path: 'admin/content/blog', renderMode: RenderMode.Client },
  { path: 'admin/content/solutions', renderMode: RenderMode.Client },
  { path: 'admin/pricing-rules', renderMode: RenderMode.Client },
  { path: 'admin/security', renderMode: RenderMode.Client },
  { path: 'admin/inquiries', renderMode: RenderMode.Client },
  { path: 'admin/inquiries/:id', renderMode: RenderMode.Client },

  // ---- Fallback: 404 page rendered on the server with a real 404 status, so
  //      crawlers that hit a dead link get an honest status code, not a 200. ----
  {
    path: '**',
    renderMode: RenderMode.Server,
    status: 404,
    headers: { 'Cache-Control': 'no-store' },
  },
];
