import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SeoService } from '../../../core/services/seo.service';
import { EngagementService } from '../../../core/services/engagement.service';
import { Engagement } from '../../../core/models/content.model';

@Component({
  selector: 'app-dashboard-list',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './dashboard-list.component.html',
  styleUrl: './dashboard-list.component.scss',
})
export class DashboardListComponent implements OnInit {
  private seo = inject(SeoService);
  private engagementService = inject(EngagementService);

  engagements = signal<Engagement[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.seo.update({
      title: 'My Projects',
      description: 'Track the status of your Neelastack projects.',
      path: '/dashboard',
      noindex: true,
    });

    this.engagementService.myEngagements().subscribe({
      next: (data) => {
        this.engagements.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
