import { Injectable, inject } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { DOCUMENT } from '@angular/common';

export interface SeoData {
  title: string;
  description: string;
  path?: string;
  image?: string;
  type?: 'website' | 'article';
  /** Set true for private/utility pages (auth flows, dashboards, secure links)
   *  that should never appear in search results, even if a URL leaks. */
  noindex?: boolean;
}

const SITE_NAME = 'Neelastack';
const SITE_URL = 'https://neelastack.com';
// Branded 1200x630 PNG (Facebook/LinkedIn/Slack don't render SVG for link previews).
// Per-page `image` overrides (e.g. a real project screenshot on a case study) take
// precedence over this default — see `update()` below.
const DEFAULT_IMAGE = `${SITE_URL}/assets/og/og-image.png`;

@Injectable({ providedIn: 'root' })
export class SeoService {
  private titleService = inject(Title);
  private meta = inject(Meta);
  private doc = inject(DOCUMENT);

  update(data: SeoData): void {
    const fullTitle = `${data.title} — ${SITE_NAME}`;
    const url = `${SITE_URL}${data.path ?? ''}`;
    const image = data.image ?? DEFAULT_IMAGE;

    this.titleService.setTitle(fullTitle);

    this.meta.updateTag({ name: 'description', content: data.description });
    this.meta.updateTag({ property: 'og:title', content: fullTitle });
    this.meta.updateTag({ property: 'og:description', content: data.description });
    this.meta.updateTag({ property: 'og:url', content: url });
    this.meta.updateTag({ property: 'og:type', content: data.type ?? 'website' });
    this.meta.updateTag({ property: 'og:image', content: image });
    this.meta.updateTag({ name: 'twitter:card', content: 'summary_large_image' });
    this.meta.updateTag({ name: 'twitter:title', content: fullTitle });
    this.meta.updateTag({ name: 'twitter:description', content: data.description });

    this.meta.updateTag({
      name: 'robots',
      content: data.noindex ? 'noindex, nofollow' : 'index, follow',
    });

    if (data.noindex) {
      // Private/utility pages don't need a canonical URL pointing search engines here.
      this.removeCanonical();
    } else {
      this.setCanonical(url);
    }
  }

  /**
   * Injects one or more JSON-LD blocks. Accepts a single schema (back-compat with
   * existing callers) or an array, since a single page legitimately needs more than
   * one type at once — e.g. a blog post emits both `TechArticle` and `BreadcrumbList`.
   * Always clears every block this service previously added before writing the new
   * set, so navigating between routes never leaves a stale schema from the last page.
   */
  setJsonLd(schemas: object | object[]): void {
    this.removeJsonLd();
    const list = Array.isArray(schemas) ? schemas : [schemas];
    list.forEach((schema, index) => {
      const script = this.doc.createElement('script');
      script.type = 'application/ld+json';
      script.id = `structured-data-${index}`;
      script.setAttribute('data-seo-managed', 'true');
      script.text = JSON.stringify(schema);
      this.doc.head.appendChild(script);
    });
  }

  private removeJsonLd(): void {
    this.doc.querySelectorAll('script[data-seo-managed="true"]').forEach((el) => el.remove());
  }

  private setCanonical(url: string): void {
    let link: HTMLLinkElement | null = this.doc.querySelector('link[rel="canonical"]');
    if (!link) {
      link = this.doc.createElement('link');
      link.setAttribute('rel', 'canonical');
      this.doc.head.appendChild(link);
    }
    link.setAttribute('href', url);
  }

  private removeCanonical(): void {
    this.doc.querySelector('link[rel="canonical"]')?.remove();
  }
}
