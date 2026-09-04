import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ContentService } from '../../../core/services/content.service';
import { SeoService } from '../../../core/services/seo.service';
import { SchemaBuilderService } from '../../../core/services/schema-builder.service';
import { NotFoundService } from '../../../core/services/not-found.service';
import { BreadcrumbComponent, BreadcrumbItem } from '../../../shared/components/breadcrumb/breadcrumb.component';
import { BlogPost, BlogPostSummary } from '../../../core/models/content.model';

@Component({
  selector: 'app-blog-detail',
  standalone: true,
  imports: [RouterLink, DatePipe, BreadcrumbComponent],
  templateUrl: './blog-detail.component.html',
  styleUrl: './blog-detail.component.scss',
})
export class BlogDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private contentService = inject(ContentService);
  private seo = inject(SeoService);
  private schema = inject(SchemaBuilderService);
  protected notFoundService = inject(NotFoundService);

  post = signal<BlogPost | null>(null);
  relatedPosts = signal<BlogPostSummary[]>([]);
  breadcrumbItems = signal<BreadcrumbItem[]>([]);

  ngOnInit(): void {
    this.notFoundService.reset();
    const slug = this.route.snapshot.paramMap.get('slug')!;
    this.contentService.getBlogPost(slug).subscribe({
      next: (post) => {
        this.post.set(post);

        const breadcrumb: BreadcrumbItem[] = [
          { name: 'Blog', path: '/blog' },
          { name: post.title, path: `/blog/${post.slug}` },
        ];
        this.breadcrumbItems.set(breadcrumb);

        this.seo.update({
          title: post.metaTitle || post.title,
          description: post.metaDescription || post.excerpt,
          path: `/blog/${post.slug}`,
          type: 'article',
          image: post.coverImageUrl,
        });

        this.seo.setJsonLd([
          this.schema.buildArticleSchema(post),
          this.schema.buildBreadcrumbSchema(breadcrumb),
        ]);
      },
      // A slug with no matching row 404s at the API (ResourceNotFoundException). Previously
      // nothing here caught that: the page rendered empty at HTTP 200 (a "soft 404", bad for
      // SEO) or, in the worst case, an uncaught SSR observable error surfaced as a raw 500.
      error: () => this.notFoundService.markNotFound(),
    });

    this.contentService.getRelatedPosts(slug).subscribe({
      next: (related) => this.relatedPosts.set(related),
      // Related posts are a nice-to-have -- a failure here shouldn't affect the 404 state
      // of the main resource above, so this just leaves the list empty rather than erroring.
      error: () => this.relatedPosts.set([]),
    });
  }
}
