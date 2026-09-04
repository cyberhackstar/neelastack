import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ContentService } from '../../../core/services/content.service';
import { SeoService } from '../../../core/services/seo.service';
import { SchemaBuilderService } from '../../../core/services/schema-builder.service';
import { NotFoundService } from '../../../core/services/not-found.service';
import { BreadcrumbComponent, BreadcrumbItem } from '../../../shared/components/breadcrumb/breadcrumb.component';
import { TechStackPage, Project, BlogPostSummary } from '../../../core/models/content.model';

@Component({
  selector: 'app-solutions-detail',
  standalone: true,
  imports: [RouterLink, BreadcrumbComponent],
  templateUrl: './solutions-detail.component.html',
  styleUrl: './solutions-detail.component.scss',
})
export class SolutionsDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private contentService = inject(ContentService);
  private seo = inject(SeoService);
  private schema = inject(SchemaBuilderService);
  protected notFoundService = inject(NotFoundService);

  page = signal<TechStackPage | null>(null);
  breadcrumbItems = signal<BreadcrumbItem[]>([]);
  relatedCaseStudies = signal<Project[]>([]);
  relatedArticles = signal<BlogPostSummary[]>([]);

  ngOnInit(): void {
    this.notFoundService.reset();
    const slug = this.route.snapshot.paramMap.get('slug')!;
    this.contentService.getSolution(slug).subscribe({
      next: (page) => {
        this.page.set(page);

        const breadcrumb: BreadcrumbItem[] = [
          { name: 'Solutions', path: '/solutions' },
          { name: page.h1Title, path: `/solutions/${page.slug}` },
        ];
        this.breadcrumbItems.set(breadcrumb);

        this.seo.update({
          title: page.metaTitle.replace(/\s*\|\s*Neelastack$/i, ''),
          description: page.metaDescription,
          path: `/solutions/${page.slug}`,
        });

        this.seo.setJsonLd([
          this.schema.buildBreadcrumbSchema(breadcrumb),
          this.schema.buildTechStackSolutionSchema(page),
        ]);

        this.loadRelatedContent(page);
      },
      // A slug with no matching row 404s at the API -- render a real 404 rather than a
      // silently empty page (see NotFoundService).
      error: () => this.notFoundService.markNotFound(),
    });
  }

  /**
   * Internal-linking silo (item 2): closes the Solutions -> Case Studies -> Articles
   * loop with real, matched content rather than a generic "see more" link. Matching
   * is a best-effort keyword overlap against the solution's tech stack — there's no
   * curated relation field, so a page with no genuine match simply shows nothing
   * (a wrong "related" link is worse than an absent one).
   */
  private loadRelatedContent(page: TechStackPage): void {
    const keywords = this.extractKeywords(page);

    this.contentService.getProjects().subscribe({
      next: (projects) => {
        const matches = projects.filter((p) =>
          p.techStack.some((tech) => keywords.some((k) => tech.toLowerCase().includes(k))),
        );
        this.relatedCaseStudies.set(matches.slice(0, 3));
      },
      // Best-effort only -- a failure here shouldn't affect the main page or its 404 state.
      error: () => this.relatedCaseStudies.set([]),
    });

    for (const keyword of keywords) {
      this.contentService.getBlogPosts(0, 3, undefined, keyword).subscribe({
        next: (result) => {
          if (result.content.length && this.relatedArticles().length === 0) {
            this.relatedArticles.set(result.content);
          }
        },
        error: () => { /* best-effort; leave relatedArticles as-is */ },
      });
    }
  }

  private extractKeywords(page: TechStackPage): string[] {
    const raw = `${page.primaryStack} ${page.secondaryStack ?? ''}`.toLowerCase();
    return raw
      .split(/[^a-z0-9.+#]+/)
      .map((token) => token.trim())
      .filter((token) => token.length > 2);
  }
}

