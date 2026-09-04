import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-browser-mockup',
  standalone: true,
  templateUrl: './browser-mockup.component.html',
  styleUrl: './browser-mockup.component.scss',
})
export class BrowserMockupComponent {
  @Input() url = '';
  @Input() screenshotUrl?: string;
  @Input() title = '';

  get displayUrl(): string {
    return this.url.replace(/^https?:\/\//, '');
  }

  get initials(): string {
    return this.title
      .split(' ')
      .filter((w) => w.length > 0)
      .slice(0, 2)
      .map((w) => w[0])
      .join('')
      .toUpperCase();
  }
}
