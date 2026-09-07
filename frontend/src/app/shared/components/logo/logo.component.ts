import { Component, Input, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ThemeService } from '../../../core/services/theme.service';

/** Theme-specific logo marks — swapped based on the active data-theme. */
const LOGO_SRC = {
  dark: 'https://res.cloudinary.com/ddrt7emvo/image/upload/neelastack_logo_dark_theme_ynwoeg.png',
  light: 'https://res.cloudinary.com/ddrt7emvo/image/upload/neelastack_logo_light_theme_riahfs.png',
} as const;

@Component({
  selector: 'app-logo',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './logo.component.html',
  styleUrl: './logo.component.scss',
})
export class LogoComponent {
  /** 'nav' is compact for the header; 'footer' adds a touch more size and a tagline. */
  @Input() variant: 'nav' | 'footer' = 'nav';

  private readonly theme = inject(ThemeService);

  /** Picks the logo mark that matches the current dark/light theme. */
  readonly logoSrc = computed(() => LOGO_SRC[this.theme.mode()]);
}
