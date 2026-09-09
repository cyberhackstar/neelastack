import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { SeoService } from '../../../core/services/seo.service';
import { EngagementService } from '../../../core/services/engagement.service';
import { Engagement } from '../../../core/models/content.model';
import { TiltDirective } from '../../../shared/directives/tilt.directive';

@Component({
  selector: 'app-dashboard-list',
  standalone: true,
  imports: [RouterLink, TiltDirective],
  templateUrl: './dashboard-list.component.html',
  styleUrl: './dashboard-list.component.scss',
})
export class DashboardListComponent implements OnInit {
  private seo = inject(SeoService);
  private engagementService = inject(EngagementService);

  engagements = signal<Engagement[]>([]);
  loading = signal(true);
  loadError = signal<string | null>(null);
  requestId = signal<string | null>(null);

  ngOnInit(): void {
    this.seo.update({
      title: 'My Projects',
      description: 'Track the status of your Neelastack projects.',
      path: '/dashboard',
      noindex: true,
    });

    this.engagementService.myEngagements().subscribe({
      next: (data) => {
        this.loadError.set(null);
        this.requestId.set(null);
        this.engagements.set(data);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.engagements.set([]);

        if (error instanceof HttpErrorResponse) {
          const backendRequestId =
            error.error && typeof error.error === 'object' && typeof error.error.requestId === 'string'
              ? error.error.requestId
              : null;
          this.requestId.set(backendRequestId);

          this.loadError.set(
            error.status === 401
              ? 'Your session has expired. Please sign in again.'
              : error.status === 403
                ? 'Your account is not permitted to view these projects.'
                : 'We could not load your projects right now. Please try again shortly.',
          );
          return;
        }

        this.requestId.set(null);
        this.loadError.set('We could not load your projects right now. Please try again shortly.');
      },
    });
  }
}
