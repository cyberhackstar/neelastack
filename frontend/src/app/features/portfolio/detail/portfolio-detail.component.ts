import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ContentService } from '../../../core/services/content.service';
import { SeoService } from '../../../core/services/seo.service';
import { SchemaBuilderService } from '../../../core/services/schema-builder.service';
import { NotFoundService } from '../../../core/services/not-found.service';
import { BreadcrumbComponent, BreadcrumbItem } from '../../../shared/components/breadcrumb/breadcrumb.component';
import { BrowserMockupComponent } from '../../../shared/components/browser-mockup/browser-mockup.component';
import { Project } from '../../../core/models/content.model';

@Component({
  selector: 'app-portfolio-detail',
  standalone: true,
  imports: [RouterLink, BrowserMockupComponent, BreadcrumbComponent],
  templateUrl: './portfolio-detail.component.html',
  styleUrl: './portfolio-detail.component.scss',
})
export class PortfolioDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private contentService = inject(ContentService);
  private seo = inject(SeoService);
  private schema = inject(SchemaBuilderService);
  protected notFoundService = inject(NotFoundService);

  project = signal<Project | null>(null);
  breadcrumbItems = signal<BreadcrumbItem[]>([]);

  ngOnInit(): void {
    this.notFoundService.reset();
    const slug = this.route.snapshot.paramMap.get('slug')!;
    this.contentService.getProject(slug).subscribe({
      next: (project) => {
        this.project.set(project);

        const breadcrumb: BreadcrumbItem[] = [
          { name: 'Portfolio', path: '/portfolio' },
          { name: project.title, path: `/portfolio/${project.slug}` },
        ];
        this.breadcrumbItems.set(breadcrumb);

        this.seo.update({
          title: project.title,
          description: project.summary,
          path: `/portfolio/${project.slug}`,
          type: 'article',
          image: project.coverImageUrl,
        });

        // Review/AggregateRating and SoftwareApplication are both conditional builders —
        // they return null (filtered out below) when there's no real review data or no
        // live deployment to back the claim, so this never emits a hollow schema block.
        const schemas = [
          this.schema.buildBreadcrumbSchema(breadcrumb),
          this.schema.buildCaseStudyReviewSchema(project),
          this.schema.buildCaseStudySoftwareSchema(project),
        ].filter((s): s is NonNullable<typeof s> => s !== null);

        this.seo.setJsonLd(schemas);
      },
      // A slug with no matching row 404s at the API -- render a real 404 rather than a
      // silently empty page (see NotFoundService).
      error: () => this.notFoundService.markNotFound(),
    });
  }
}
