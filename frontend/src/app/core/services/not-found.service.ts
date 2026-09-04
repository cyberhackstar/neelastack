import { Injectable, inject, signal, RESPONSE_INIT } from '@angular/core';
import { SeoService } from './seo.service';

/**
 * Dynamic detail routes (portfolio/:slug, blog/:slug, solutions/:slug) are all rendered
 * server-side (see app.routes.server.ts) because their content is fresh from the DB on
 * every request. Previously, a slug with no matching row just left the page's signal
 * unset and the component subscribed to the fetch with no error handler at all -- the
 * underlying HttpClient GET 404s (ResourceNotFoundException -> GlobalExceptionHandler),
 * but with nothing catching it client-side this either surfaced as an empty-looking page
 * served with a *200* status (actively harmful for SEO -- a "soft 404"), or in the worst
 * case crashed the SSR render pass into a raw 500.
 *
 * This service gives every detail component a single call to make on a 404: it marks the
 * page noindex (reusing SeoService, which already supports this) and, when running on the
 * server, sets the actual HTTP response status to 404 via Angular's RESPONSE_INIT token --
 * so a valid-looking URL for a resource that doesn't exist now honestly reports 404 to
 * both users and crawlers, instead of a soft-404 or a 500.
 */
@Injectable({ providedIn: 'root' })
export class NotFoundService {
  private seo = inject(SeoService);
  // RESPONSE_INIT is null during CSR, SSG, and build -- only meaningful during a real SSR
  // request, which is exactly when we need to set a status code at all.
  private responseInit = inject(RESPONSE_INIT, { optional: true });

  readonly notFound = signal(false);

  /** Call from a detail component's HTTP error handler when the backing resource is missing. */
  markNotFound(): void {
    this.notFound.set(true);
    this.seo.update({
      title: 'Page not found',
      description: 'The page you were looking for could not be found.',
      noindex: true,
    });
    if (this.responseInit) {
      this.responseInit.status = 404;
    }
  }

  reset(): void {
    this.notFound.set(false);
  }
}
