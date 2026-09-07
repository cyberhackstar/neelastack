import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';

export type ThemeMode = 'dark' | 'light';

const THEME_KEY = 'neelastack_theme';

/**
 * Keeps the site's dark/light preference in sync across the toggle,
 * localStorage, and the `data-theme` attribute the whole stylesheet reads
 * from. The very first paint is handled separately by an inline script in
 * index.html (see that file) so there's no flash before Angular boots —
 * this service takes over from there and is what the toggle button drives.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);

  readonly mode = signal<ThemeMode>(this.readInitialMode());

  constructor() {
    this.applyToDocument(this.mode());
  }

  toggle(): void {
    this.setMode(this.mode() === 'dark' ? 'light' : 'dark');
  }

  setMode(mode: ThemeMode): void {
    this.mode.set(mode);
    this.applyToDocument(mode);
    if (this.isBrowser) {
      try {
        window.localStorage.setItem(THEME_KEY, mode);
      } catch {
        // Private browsing / storage disabled — the toggle still works for
        // the current session via the signal, it just won't persist.
      }
    }
  }

  private readInitialMode(): ThemeMode {
    if (!this.isBrowser) return 'dark';

    // The inline bootstrap script already set data-theme on <html> before
    // Angular loaded — read that back so hydration matches what's on screen
    // instead of re-deriving it (and risking a mismatch/flash).
    const attr = this.document.documentElement.getAttribute('data-theme');
    if (attr === 'light' || attr === 'dark') return attr;

    try {
      const stored = window.localStorage.getItem(THEME_KEY);
      if (stored === 'light' || stored === 'dark') return stored;
    } catch {
      // ignore
    }

    return window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
  }

  private applyToDocument(mode: ThemeMode): void {
    this.document.documentElement.setAttribute('data-theme', mode);
  }
}
