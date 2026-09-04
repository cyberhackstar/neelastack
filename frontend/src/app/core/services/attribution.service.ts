import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { ActivatedRoute } from '@angular/router';

const STORAGE_KEY = 'neelastack_attribution';

export interface Attribution {
  utmSource?: string;
  utmMedium?: string;
  utmCampaign?: string;
  referrer?: string;
  landingPage?: string;
}

/**
 * Captures first-touch attribution (master prompt section 47) the moment a visitor
 * lands with UTM params, and keeps it in localStorage so a lead submitted days later
 * (e.g. after browsing services/portfolio first) still carries where they came from.
 * Browser-only — SSR has no localStorage and no real referrer/query-param context to
 * capture from the first request that matters here (the client-side landing).
 */
@Injectable({ providedIn: 'root' })
export class AttributionService {
  private platformId = inject(PLATFORM_ID);

  /** Call once, near app start (e.g. in AppComponent), with the initial route snapshot. */
  captureFirstTouch(route: ActivatedRoute): void {
    if (!isPlatformBrowser(this.platformId)) return;
    if (localStorage.getItem(STORAGE_KEY)) return; // first touch only — don't overwrite

    const params = route.snapshot.queryParamMap;
    const utmSource = params.get('utm_source') ?? undefined;
    const utmMedium = params.get('utm_medium') ?? undefined;
    const utmCampaign = params.get('utm_campaign') ?? undefined;

    if (!utmSource && !utmMedium && !utmCampaign && !document.referrer) {
      return; // nothing worth storing (e.g. a direct visit with no params)
    }

    const attribution: Attribution = {
      utmSource,
      utmMedium,
      utmCampaign,
      referrer: document.referrer || undefined,
      landingPage: window.location.pathname + window.location.search,
    };

    localStorage.setItem(STORAGE_KEY, JSON.stringify(attribution));
  }

  get(): Attribution {
    if (!isPlatformBrowser(this.platformId)) return {};
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as Attribution) : {};
    } catch {
      return {};
    }
  }
}
