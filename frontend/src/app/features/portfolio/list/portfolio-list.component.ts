import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ContentService } from '../../../core/services/content.service';
import { SeoService } from '../../../core/services/seo.service';
import { SchemaBuilderService } from '../../../core/services/schema-builder.service';
import { BreadcrumbComponent, BreadcrumbItem } from '../../../shared/components/breadcrumb/breadcrumb.component';
import { BrowserMockupComponent } from '../../../shared/components/browser-mockup/browser-mockup.component';
import { Project } from '../../../core/models/content.model';

@Component({
  selector: 'app-portfolio-list',
  standalone: true,
  imports: [RouterLink, BrowserMockupComponent, BreadcrumbComponent],
  templateUrl: './portfolio-list.component.html',
  styleUrl: './portfolio-list.component.scss',
})
export class PortfolioListComponent implements OnInit {
  private contentService = inject(ContentService);
  private seo = inject(SeoService);
  private schema = inject(SchemaBuilderService);

  projects = signal<Project[]>([]);
  readonly breadcrumbItems: BreadcrumbItem[] = [{ name: 'Portfolio', path: '/portfolio' }];

  ngOnInit(): void {
    this.seo.update({
      title: 'Portfolio',
      description: 'Case studies from recent Spring Boot and Angular builds — the problem, the solution, and the outcome.',
      path: '/portfolio',
    });
    this.contentService.getProjects().subscribe((data) => {
      this.projects.set(data);
      this.seo.setJsonLd(this.schema.buildBreadcrumbSchema(this.breadcrumbItems));
    });
  }
}
