import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ContentService } from '../../../../core/services/content.service';
import { TechStackPage } from '../../../../core/models/content.model';
import { SeoService } from '../../../../core/services/seo.service';

/**
 * Admin CMS for the programmatic-SEO solution silo (`tech_stack_pages`).
 *
 * The backend CRUD (`TechStackPageService` / `AdminContentController` `/admin/solutions/**`)
 * and the frontend `ContentService` methods already existed — this component was the one
 * missing piece (master-prompt section 22: "Implement the missing admin interface:
 * /admin/content/solutions"). Field set and layout intentionally mirror
 * `AdminServicesComponent`/`AdminProjectsComponent` so the CMS feels consistent, extended
 * with the solution-page-specific fields (H1/meta/body/stack/industry/use cases/price).
 *
 * `useCases` is stored as a `List<String>` on the backend but edited here as one
 * newline-per-use-case textarea for a simpler editing experience — converted at the
 * form/API boundary only.
 */
@Component({
  selector: 'app-admin-solutions',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './admin-solutions.component.html',
  styleUrl: './admin-solutions.component.scss',
})
export class AdminSolutionsComponent implements OnInit {
  private contentService = inject(ContentService);
  private seo = inject(SeoService);
  private fb = inject(FormBuilder);

  solutions = signal<TechStackPage[]>([]);
  loading = signal(true);
  editingId = signal<string | null>(null);
  saving = signal(false);
  formOpen = signal(false);
  error = signal<string | null>(null);

  form = this.fb.nonNullable.group({
    slug: ['', Validators.required],
    h1Title: ['', Validators.required],
    metaTitle: ['', [Validators.required, Validators.maxLength(160)]],
    metaDescription: ['', [Validators.required, Validators.maxLength(300)]],
    intro: ['', Validators.required],
    bodyContent: ['', Validators.required],
    primaryStack: ['', Validators.required],
    secondaryStack: [''],
    targetIndustry: [''],
    useCasesText: [''],
    startingPrice: [''],
    displayOrder: [0],
    published: [false],
  });

  ngOnInit(): void {
    this.seo.update({ title: 'Manage Solutions', description: 'Neelastack solution-page CMS.', noindex: true });
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.contentService.listAllSolutions().subscribe({
      next: (data) => {
        this.solutions.set([...data].sort((a, b) => a.displayOrder - b.displayOrder));
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load solution pages.');
        this.loading.set(false);
      },
    });
  }

  slugify(): void {
    const title = this.form.controls.h1Title.value;
    if (title && !this.editingId()) {
      this.form.controls.slug.setValue(
        title.toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, ''),
      );
    }
  }

  startCreate(): void {
    this.editingId.set(null);
    this.error.set(null);
    this.form.reset({
      slug: '',
      h1Title: '',
      metaTitle: '',
      metaDescription: '',
      intro: '',
      bodyContent: '',
      primaryStack: '',
      secondaryStack: '',
      targetIndustry: '',
      useCasesText: '',
      startingPrice: '',
      displayOrder: this.solutions().length,
      published: false,
    });
    this.formOpen.set(true);
  }

  startEdit(page: TechStackPage): void {
    this.editingId.set(page.id);
    this.error.set(null);
    this.form.setValue({
      slug: page.slug,
      h1Title: page.h1Title,
      metaTitle: page.metaTitle,
      metaDescription: page.metaDescription,
      intro: page.intro,
      bodyContent: page.bodyContent,
      primaryStack: page.primaryStack,
      secondaryStack: page.secondaryStack ?? '',
      targetIndustry: page.targetIndustry ?? '',
      useCasesText: (page.useCases ?? []).join('\n'),
      startingPrice: page.startingPrice ?? '',
      displayOrder: page.displayOrder,
      published: page.published,
    });
    this.formOpen.set(true);
  }

  cancel(): void {
    this.formOpen.set(false);
    this.editingId.set(null);
    this.error.set(null);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.error.set(null);
    const raw = this.form.getRawValue();
    const payload = {
      slug: raw.slug,
      h1Title: raw.h1Title,
      metaTitle: raw.metaTitle,
      metaDescription: raw.metaDescription,
      intro: raw.intro,
      bodyContent: raw.bodyContent,
      primaryStack: raw.primaryStack,
      secondaryStack: raw.secondaryStack || undefined,
      targetIndustry: raw.targetIndustry || undefined,
      useCases: raw.useCasesText
        .split('\n')
        .map((line) => line.trim())
        .filter((line) => line.length > 0),
      startingPrice: raw.startingPrice || undefined,
      displayOrder: raw.displayOrder,
      published: raw.published,
    };

    const editingId = this.editingId();
    const request = editingId
      ? this.contentService.updateSolution(editingId, payload)
      : this.contentService.createSolution(payload);

    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.load();
      },
      error: () => {
        this.saving.set(false);
        this.error.set('Save failed — check required fields and try again.');
      },
    });
  }

  remove(page: TechStackPage): void {
    if (!confirm(`Delete "${page.h1Title}"? This can't be undone.`)) return;
    this.contentService.deleteSolution(page.id).subscribe(() => this.load());
  }

  /** /admin route can't reach draft pages directly (public endpoint only serves published ones). */
  previewHref(page: TechStackPage): string | null {
    return page.published ? `/solutions/${page.slug}` : null;
  }
}
