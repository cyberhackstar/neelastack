import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ContentService } from '../../../core/services/content.service';
import { SeoService } from '../../../core/services/seo.service';
import { SchemaBuilderService } from '../../../core/services/schema-builder.service';
import { BreadcrumbComponent, BreadcrumbItem } from '../../../shared/components/breadcrumb/breadcrumb.component';
import { TechStackPage } from '../../../core/models/content.model';

@Component({
  selector: 'app-solutions-list',
  standalone: true,
  imports: [RouterLink, BreadcrumbComponent],
  templateUrl: './solutions-list.component.html',
  styleUrl: './solutions-list.component.scss',
})
export class SolutionsListComponent implements OnInit {
  private contentService = inject(ContentService);
  private seo = inject(SeoService);
  private schema = inject(SchemaBuilderService);

  pages = signal<TechStackPage[]>([]);
  readonly breadcrumbItems: BreadcrumbItem[] = [{ name: 'Solutions', path: '/solutions' }];

  ngOnInit(): void {
    this.seo.update({
      title: 'Solutions by Tech Stack',
      description:
        'Enterprise development solutions by tech stack and industry — Spring Boot, Angular, and the combinations we specialize in, each with fixed scope and a written proposal.',
      path: '/solutions',
    });

    this.contentService.getSolutions().subscribe((data) => {
      this.pages.set(data);
      this.seo.setJsonLd(this.schema.buildBreadcrumbSchema(this.breadcrumbItems));
    });
  }
}
