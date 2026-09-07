import { Directive, ElementRef, HostListener, Inject, OnDestroy, OnInit, PLATFORM_ID, Renderer2 } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

/**
 * Upgrades every `.card-tilt` surface (service/blog/portfolio/team/dashboard
 * cards, the CTA path cards on the home page, etc.) from the old static
 * `:hover { rotateX(2deg) rotateY(-2deg) }` rule into a cursor-tracking 3D
 * tilt — the same "follow the pointer" depth used in the reference hero
 * card, applied site-wide via a class selector instead of touching every
 * template.
 *
 * Matches the rest of the codebase's motion language: desktop-with-a-precise
 * -pointer only, off for `prefers-reduced-motion`, and it degrades to
 * nothing extra — the original CSS `:hover` tilt in styles.scss still fires
 * for keyboard focus, touch, and reduced-motion visitors, so nobody
 * regresses. See HomeComponent's hero parallax for the same guard pattern.
 */
@Directive({
  selector: '.card-tilt',
  standalone: true,
})
export class TiltDirective implements OnInit, OnDestroy {
  /** Degrees at the extreme edge of the card — kept modest on purpose. */
  private readonly maxTilt = 6;
  private enabled = false;
  private rafId: number | null = null;

  constructor(
    private readonly el: ElementRef<HTMLElement>,
    private readonly renderer: Renderer2,
    @Inject(PLATFORM_ID) private readonly platformId: object,
  ) {}

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    const finePointer = window.matchMedia('(pointer: fine)').matches;
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    this.enabled = finePointer && !reducedMotion;
  }

  @HostListener('mousemove', ['$event'])
  onMouseMove(event: MouseEvent): void {
    if (!this.enabled) return;
    if (this.rafId !== null) cancelAnimationFrame(this.rafId);

    const host = this.el.nativeElement;
    this.rafId = requestAnimationFrame(() => {
      const rect = host.getBoundingClientRect();
      const xRatio = (event.clientX - rect.left) / rect.width - 0.5;
      const yRatio = (event.clientY - rect.top) / rect.height - 0.5;
      const rotateY = (xRatio * this.maxTilt).toFixed(2);
      const rotateX = (-yRatio * this.maxTilt).toFixed(2);
      this.renderer.setStyle(
        host,
        'transform',
        `perspective(800px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) translateY(-4px)`,
      );
    });
  }

  @HostListener('mouseleave')
  onMouseLeave(): void {
    if (!this.enabled) return;
    // Drop back to the plain CSS rule instead of hard-coding an identity
    // transform, so anything else that targets .card-tilt in stylesheets
    // stays in control once the pointer leaves.
    this.renderer.removeStyle(this.el.nativeElement, 'transform');
  }

  ngOnDestroy(): void {
    if (this.rafId !== null) cancelAnimationFrame(this.rafId);
  }
}
