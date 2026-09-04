import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ContentService } from '../../../core/services/content.service';
import { SeoService } from '../../../core/services/seo.service';
import { SchemaBuilderService } from '../../../core/services/schema-builder.service';
import { BreadcrumbComponent, BreadcrumbItem } from '../../../shared/components/breadcrumb/breadcrumb.component';
import { BlogPostSummary } from '../../../core/models/content.model';

@Component({
  selector: 'app-blog-list',
  standalone: true,
  imports: [RouterLink, FormsModule, BreadcrumbComponent],
  templateUrl: './blog-list.component.html',
  styleUrl: './blog-list.component.scss',
})
export class BlogListComponent implements OnInit {
  private contentService = inject(ContentService);
  private seo = inject(SeoService);
  private schema = inject(SchemaBuilderService);

  posts = signal<BlogPostSummary[]>([]);
  searchTerm = signal('');
  activeTag = signal<string | null>(null);
  readonly breadcrumbItems: BreadcrumbItem[] = [{ name: 'Blog', path: '/blog' }];

  get availableTags(): string[] {
    const tagSet = new Set<string>();
    this.posts().forEach((p) => p.tags?.forEach((t) => tagSet.add(t)));
    return Array.from(tagSet).sort();
  }

  ngOnInit(): void {
    this.seo.update({
      title: 'Blog',
      description: 'Notes on Spring Boot, Angular, and building production web applications.',
      path: '/blog',
    });
    this.seo.setJsonLd(this.schema.buildBreadcrumbSchema(this.breadcrumbItems));
    this.load();
  }

  private load(): void {
    this.contentService
      .getBlogPosts(0, 20, this.searchTerm() || undefined, this.activeTag() || undefined)
      .subscribe((page) => this.posts.set(page.content));
  }

  search(): void {
    this.load();
  }

  filterByTag(tag: string): void {
    this.activeTag.set(this.activeTag() === tag ? null : tag);
    this.load();
  }

  clearFilters(): void {
    this.searchTerm.set('');
    this.activeTag.set(null);
    this.load();
  }
}
