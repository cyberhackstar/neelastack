import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';

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
}
