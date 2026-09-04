import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PublicQuotationService } from '../../core/services/public-quotation.service';
import { SeoService } from '../../core/services/seo.service';
import { PublicQuotation } from '../../core/models/content.model';

@Component({
  selector: 'app-quote',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './quote.component.html',
  styleUrl: './quote.component.scss',
})
export class QuoteComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private quotationService = inject(PublicQuotationService);
  private seo = inject(SeoService);

  quotation = signal<PublicQuotation | null>(null);
  loading = signal(true);
  notFound = signal(false);
  responding = signal(false);
  showRejectReason = signal(false);
  rejectReason = signal('');

  private token = '';

  ngOnInit(): void {
    this.seo.update({
      title: 'Your Quotation',
      description: 'Review and respond to your Neelastack project quotation.',
      path: '/quote',
      noindex: true,
    });

    this.token = this.route.snapshot.paramMap.get('token') ?? '';
    this.load();
  }

  private load(): void {
    if (!this.token) {
      this.notFound.set(true);
      this.loading.set(false);
      return;
    }
    this.quotationService.get(this.token).subscribe({
      next: (data) => {
        this.quotation.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.notFound.set(true);
        this.loading.set(false);
      },
    });
  }

  accept(): void {
    if (!confirm('Accept this quotation? This confirms you want to proceed at the price and scope shown.')) {
      return;
    }
    this.responding.set(true);
    this.quotationService.respond(this.token, true).subscribe({
      next: (data) => {
        this.quotation.set(data);
        this.responding.set(false);
      },
      error: () => this.responding.set(false),
    });
  }

  reject(): void {
    this.responding.set(true);
    this.quotationService.respond(this.token, false, this.rejectReason() || undefined).subscribe({
      next: (data) => {
        this.quotation.set(data);
        this.responding.set(false);
      },
      error: () => this.responding.set(false),
    });
  }
}
