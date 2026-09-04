import { Component, ElementRef, HostListener, ViewChild, effect, inject, signal } from '@angular/core';
import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { PLATFORM_ID } from '@angular/core';
import { Router, NavigationEnd, RouterLink, RouterLinkActive } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { LogoComponent } from '../logo/logo.component';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, LogoComponent],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.scss',
})
export class NavbarComponent {
  auth = inject(AuthService);
  private router = inject(Router);
  private document = inject(DOCUMENT);
  private platformId = inject(PLATFORM_ID);

  menuOpen = signal(false);
  scrolled = signal(false);

  @ViewChild('menuToggleBtn') private menuToggleBtn?: ElementRef<HTMLButtonElement>;
  @ViewChild('mobilePanel') private mobilePanel?: ElementRef<HTMLElement>;

  constructor() {
    // Close the mobile menu automatically whenever a navigation completes —
    // otherwise tapping a link leaves the overlay open behind the new page.
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => {
      this.menuOpen.set(false);
    });

    // Lock background scroll while the mobile panel is open, so the page
    // behind it doesn't scroll along with the overlay's own content.
    effect(() => {
      if (!isPlatformBrowser(this.platformId)) return;
      this.document.body.style.overflow = this.menuOpen() ? 'hidden' : '';
    });

    // Move focus into the panel when it opens (first link), and back to the
    // toggle button only when it was *previously open* and is now closing —
    // not on initial component creation, which also runs this effect once
    // with menuOpen()=false and would otherwise steal focus to the
    // hamburger button on every page load.
    let wasOpen = false;
    effect(() => {
      if (!isPlatformBrowser(this.platformId)) return;
      const open = this.menuOpen();
      const transitioned = open !== wasOpen;
      wasOpen = open;
      if (!transitioned) return;

      setTimeout(() => {
        if (open) {
          const firstLink = this.mobilePanel?.nativeElement.querySelector<HTMLElement>(
            'a, button'
          );
          firstLink?.focus();
        } else {
          this.menuToggleBtn?.nativeElement.focus();
        }
      });
    });
  }

  toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  closeMenu(): void {
    this.menuOpen.set(false);
  }

  // Escape key closes the menu, same as tapping the backdrop.
  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closeMenu();
  }

  // Keep Tab from escaping the open panel into the (hidden but still-present)
  // page behind it — a lightweight focus trap rather than a full library.
  onPanelKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Tab' || !this.mobilePanel) return;
    const focusable = Array.from(
      this.mobilePanel.nativeElement.querySelectorAll<HTMLElement>('a, button')
    ).filter((el) => !el.hasAttribute('disabled'));
    if (focusable.length === 0) return;

    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const active = this.document.activeElement;

    if (event.shiftKey && active === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && active === last) {
      event.preventDefault();
      first.focus();
    }
  }

  // Tighten the bar and strengthen the blur/shadow once the page has
  // scrolled past the hero, so it reads as a deliberate "docked" state
  // rather than a static bar sitting on top of the content.
  @HostListener('window:scroll')
  onScroll(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    this.scrolled.set(this.document.defaultView!.scrollY > 8);
  }
}
