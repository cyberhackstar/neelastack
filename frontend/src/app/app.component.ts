import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { ActivatedRoute, RouterOutlet } from '@angular/router';
import { NavbarComponent } from './shared/components/navbar/navbar.component';
import { FooterComponent } from './shared/components/footer/footer.component';
import { VerifyBannerComponent } from './shared/components/verify-banner/verify-banner.component';
import { StepUpModalComponent } from './shared/components/step-up-modal/step-up-modal.component';
import { GaAnalyticsService } from './core/services/ga-analytics.service';
import { AttributionService } from './core/services/attribution.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent, FooterComponent, VerifyBannerComponent, StepUpModalComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent implements OnInit {
  private ga = inject(GaAnalyticsService);
  private attribution = inject(AttributionService);
  private route = inject(ActivatedRoute);
  private document = inject(DOCUMENT);
  private destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.ga.init();
    this.attribution.captureFirstTouch(this.route);
    this.installMobileInputZoomReset();
  }

  /**
   * Mobile Safari/Chrome can retain the temporary focus zoom after a user
   * leaves a form control. Reset just after the focus moves away while
   * preserving the normal zoom-on-focus behavior. Runs only in browsers.
   */
  private installMobileInputZoomReset(): void {
    if (typeof window === 'undefined' || !window.visualViewport) return;

    const handler = (event: FocusEvent) => {
      const target = event.target as HTMLElement | null;
      if (!target || !target.matches('input, select, textarea')) return;

      window.setTimeout(() => {
        const active = this.document.activeElement as HTMLElement | null;
        if (active?.matches('input, select, textarea')) return;

        const viewport = this.document.querySelector<HTMLMetaElement>('meta[name="viewport"]');
        if (!viewport) return;

        const original = viewport.content || 'width=device-width, initial-scale=1';
        viewport.setAttribute('content', `${original}, maximum-scale=1`);
        window.setTimeout(() => {
          viewport.setAttribute('content', original);
        }, 240);
      }, 80);
    };

    this.document.addEventListener('focusout', handler, true);
    this.destroyRef.onDestroy(() => this.document.removeEventListener('focusout', handler, true));
  }
}
