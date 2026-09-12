import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { SeoService } from '../../../core/services/seo.service';
import { EngagementService } from '../../../core/services/engagement.service';
import { Engagement, EngagementStatus } from '../../../core/models/content.model';

@Component({
  selector: 'app-admin-engagements',
  standalone: true,
  imports: [DatePipe, RouterLink],
  templateUrl: './admin-engagements.component.html',
  styleUrl: './admin-engagements.component.scss',
})
export class AdminEngagementsComponent implements OnInit {
  private readonly engagementService = inject(EngagementService);
  private readonly seo = inject(SeoService);

  engagements = signal<Engagement[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  actionError = signal<string | null>(null);
  updatingId = signal<string | null>(null);

  readonly statuses: EngagementStatus[] = [
    'ONBOARDING',
    'IN_PROGRESS',
    'REVIEW',
    'ON_HOLD',
    'COMPLETED',
  ];

  ngOnInit(): void {
    this.seo.update({
      title: 'Client Projects',
      description: 'Manage Neelastack client projects and engagements.',
      noindex: true,
    });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);

    this.engagementService.listAllForAdmin().subscribe({
      next: (rows) => {
        this.engagements.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Could not load client projects. Please refresh and try again.');
      },
    });
  }

  updateStatus(engagement: Engagement, status: string): void {
    if (!this.statuses.includes(status as EngagementStatus) || status === engagement.status) {
      return;
    }

    const nextStatus = status as EngagementStatus;
    const previous = engagement.status;
    this.actionError.set(null);
    this.updatingId.set(engagement.id);

    this.engagementService.updateEngagementStatus(engagement.id, nextStatus).subscribe({
      next: (updated) => {
        this.engagements.update((rows) =>
          rows.map((row) => row.id === updated.id ? updated : row),
        );
        this.updatingId.set(null);
      },
      error: () => {
        // Keep the local value unchanged; the select is rebound from the current signal.
        this.actionError.set(`Could not change “${engagement.title.trim()}” from ${previous}.`);
        this.updatingId.set(null);
      },
    });
  }

  trim(value: string | undefined): string {
    return value?.trim() || '—';
  }

  statusLabel(status: EngagementStatus): string {
    return status.replace(/_/g, ' ');
  }
}
