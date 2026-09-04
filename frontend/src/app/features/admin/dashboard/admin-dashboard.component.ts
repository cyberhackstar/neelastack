import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { AnalyticsService } from '../../../core/services/analytics.service';
import {
  AnalyticsSummary,
  AttributionBreakdown,
  AttributionDimension,
  FollowUpTask,
  SalesIntelligence,
} from '../../../core/models/content.model';
import { SeoService } from '../../../core/services/seo.service';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [RouterLink, DecimalPipe],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.scss',
})
export class AdminDashboardComponent implements OnInit {
  private analyticsService = inject(AnalyticsService);
  private seo = inject(SeoService);

  // Each panel has its own signal + loading flag, loaded independently, so a slow
  // attribution query never blocks the KPI row (or any other panel) from rendering.
  summary = signal<AnalyticsSummary | null>(null);
  loading = signal(true);

  salesIntelligence = signal<SalesIntelligence | null>(null);
  salesIntelligenceLoading = signal(true);

  attributionDimension = signal<AttributionDimension>('SOURCE');
  attributionRows = signal<AttributionBreakdown[] | null>(null);
  attributionLoading = signal(true);

  followUps = signal<FollowUpTask[] | null>(null);
  followUpsLoading = signal(true);
  followUpActionError = signal<string | null>(null);

  readonly dimensions: { value: AttributionDimension; label: string }[] = [
    { value: 'SOURCE', label: 'Source' },
    { value: 'MEDIUM', label: 'Medium' },
    { value: 'CAMPAIGN', label: 'Campaign' },
    { value: 'LANDING_PAGE', label: 'Landing page' },
  ];

  ngOnInit(): void {
    this.seo.update({ title: 'Admin Dashboard', description: 'Neelastack admin dashboard.', noindex: true });

    this.analyticsService.getSummary().subscribe({
      next: (data) => {
        this.summary.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });

    this.analyticsService.getSalesIntelligence().subscribe({
      next: (data) => {
        this.salesIntelligence.set(data);
        this.salesIntelligenceLoading.set(false);
      },
      error: () => this.salesIntelligenceLoading.set(false),
    });

    this.loadAttribution('SOURCE');
    this.loadFollowUps();
  }

  loadAttribution(dimension: AttributionDimension): void {
    this.attributionDimension.set(dimension);
    this.attributionLoading.set(true);
    this.analyticsService.getRevenueByAttribution(dimension).subscribe({
      next: (rows) => {
        this.attributionRows.set(rows);
        this.attributionLoading.set(false);
      },
      error: () => this.attributionLoading.set(false),
    });
  }

  loadFollowUps(): void {
    this.followUpsLoading.set(true);
    this.analyticsService.getFollowUps().subscribe({
      next: (tasks) => {
        this.followUps.set(tasks);
        this.followUpsLoading.set(false);
      },
      error: () => this.followUpsLoading.set(false),
    });
  }

  markDone(task: FollowUpTask): void {
    this.followUpActionError.set(null);
    const prior = this.followUps();
    // Optimistic removal — the panel updates immediately; roll back on failure.
    this.followUps.set((prior ?? []).filter((t) => t.quotationId !== task.quotationId));
    this.analyticsService.dismissFollowUp(task.quotationId).subscribe({
      error: () => {
        this.followUps.set(prior);
        this.followUpActionError.set('Could not mark that as done. Try again.');
      },
    });
  }

  snoozeOneDay(task: FollowUpTask): void {
    this.followUpActionError.set(null);
    const until = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();
    const prior = this.followUps();
    this.followUps.set((prior ?? []).filter((t) => t.quotationId !== task.quotationId));
    this.analyticsService.snoozeFollowUp(task.quotationId, until).subscribe({
      error: () => {
        this.followUps.set(prior);
        this.followUpActionError.set('Could not snooze that. Try again.');
      },
    });
  }

  currentDimensionLabel(): string {
    return this.dimensions.find((d) => d.value === this.attributionDimension())?.label ?? 'Source';
  }

  reasonLabel(reason: FollowUpTask['reason']): string {
    return reason === 'UNVIEWED_REMINDER' ? 'Never opened' : 'Opened, no response';
  }

  statusEntries(): [string, number][] {
    const summary = this.summary();
    return summary ? Object.entries(summary.engagementsByStatus) : [];
  }

  maxStatusCount(): number {
    return Math.max(1, ...this.statusEntries().map(([, count]) => count));
  }
}
