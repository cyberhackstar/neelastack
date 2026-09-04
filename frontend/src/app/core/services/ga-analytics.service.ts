import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser, DOCUMENT } from '@angular/common';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs';
import { environment } from '../../../environments/environment';

declare global {
  interface Window {
    dataLayer?: unknown[];
    gtag?: (...args: unknown[]) => void;
  }
}

/**
 * Loads Google Analytics 4 only if a measurement ID is configured, and only
 * in the browser (never during SSR). Tracks page views on route changes
 * since this is a single-page app — GA4's default pageview-on-load doesn't
 * fire again on client-side navigation without this.
 */
@Injectable({ providedIn: 'root' })
export class GaAnalyticsService {
  private platformId = inject(PLATFORM_ID);
  private document = inject(DOCUMENT);
  private router = inject(Router);

  private readonly measurementId = environment.gaMeasurementId;

  init(): void {
    if (!this.measurementId || !isPlatformBrowser(this.platformId)) {
      return;
    }

    this.injectScript();
    this.trackRouteChanges();
  }

  private injectScript(): void {
    const script = this.document.createElement('script');
    script.async = true;
    script.src = `https://www.googletagmanager.com/gtag/js?id=${this.measurementId}`;
    this.document.head.appendChild(script);

    window.dataLayer = window.dataLayer || [];
    window.gtag = function gtag(...args: unknown[]) {
      window.dataLayer!.push(args);
    };
    window.gtag('js', new Date());
    window.gtag('config', this.measurementId, { send_page_view: false });
  }

  private trackRouteChanges(): void {
    this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe((event) => {
      const navEvent = event as NavigationEnd;
      window.gtag?.('event', 'page_view', {
        page_path: navEvent.urlAfterRedirects,
        page_location: this.document.location.href,
      });
    });
  }

  /**
   * Fires a custom GA4 event (master prompt section 46 — estimator_start,
   * estimator_complete, contact_submit, etc.). No-op during SSR or when GA isn't
   * configured, same as the rest of this service.
   */
  trackEvent(name: string, params: Record<string, unknown> = {}): void {
    if (!this.measurementId || !isPlatformBrowser(this.platformId)) {
      return;
    }
    window.gtag?.('event', name, params);
  }
}
