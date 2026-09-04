import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ContentService } from '../../../../core/services/content.service';
import { BlogPostSummary } from '../../../../core/models/content.model';
import { SeoService } from '../../../../core/services/seo.service';

@Component({
  selector: 'app-admin-blog',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './admin-blog.component.html',
  styleUrl: './admin-blog.component.scss',
})
export class AdminBlogComponent implements OnInit {
  private contentService = inject(ContentService);
  private seo = inject(SeoService);
  private fb = inject(FormBuilder);

  posts = signal<BlogPostSummary[]>([]);
  loading = signal(true);
  editingId = signal<string | null>(null);
  saving = signal(false);
  formOpen = signal(false);

  form = this.fb.nonNullable.group({
    title: ['', Validators.required],
    slug: ['', Validators.required],
    excerpt: ['', Validators.required],
    content: ['', Validators.required],
    coverImageUrl: [''],
    authorName: ['Bhawesh Sharma'],
    category: [''],
    tagsText: [''],
    metaTitle: ['', Validators.required],
    metaDescription: ['', Validators.required],
    published: [false],
  });

  ngOnInit(): void {
    this.seo.update({ title: 'Manage Blog', description: 'Neelastack blog CMS.', noindex: true });
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.contentService.listAllBlogPosts().subscribe({
      next: (data) => {
        this.posts.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  slugify(): void {
    const title = this.form.controls.title.value;
    if (title && !this.editingId()) {
      this.form.controls.slug.setValue(
        title.toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, ''),
      );
    }
    if (title && !this.form.controls.metaTitle.value) {
      this.form.controls.metaTitle.setValue(title);
    }
  }

  startCreate(): void {
    this.editingId.set(null);
    this.form.reset({
      title: '', slug: '', excerpt: '', content: '', coverImageUrl: '',
      authorName: 'Bhawesh Sharma', category: '', tagsText: '', metaTitle: '', metaDescription: '', published: false,
    });
    this.formOpen.set(true);
  }

  startEdit(post: BlogPostSummary): void {
    this.editingId.set(post.id);
    this.contentService.getBlogPostById(post.id).subscribe((full) => {
      this.form.setValue({
        title: full.title,
        slug: full.slug,
        excerpt: full.excerpt,
        content: full.content,
        coverImageUrl: full.coverImageUrl ?? '',
        authorName: full.authorName ?? 'Bhawesh Sharma',
        category: full.category ?? '',
        tagsText: (full.tags ?? []).join(', '),
        metaTitle: full.metaTitle,
        metaDescription: full.metaDescription,
        published: full.published ?? false,
      });
    });
    this.formOpen.set(true);
  }

  cancel(): void {
    this.formOpen.set(false);
    this.editingId.set(null);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    const raw = this.form.getRawValue();
    const payload = {
      ...raw,
      tags: raw.tagsText.split(',').map((t) => t.trim()).filter(Boolean),
    };
    delete (payload as any).tagsText;

    const editingId = this.editingId();
    const request = editingId
      ? this.contentService.updateBlogPost(editingId, payload as any)
      : this.contentService.createBlogPost(payload as any);

    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.load();
      },
      error: () => this.saving.set(false),
    });
  }

  remove(post: BlogPostSummary): void {
    if (!confirm(`Delete "${post.title}"? This can't be undone.`)) return;
    this.contentService.deleteBlogPost(post.id).subscribe(() => this.load());
  }
}
