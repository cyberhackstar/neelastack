import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ContentService } from '../../../../core/services/content.service';
import { Project } from '../../../../core/models/content.model';
import { SeoService } from '../../../../core/services/seo.service';

@Component({
  selector: 'app-admin-projects',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './admin-projects.component.html',
  styleUrl: './admin-projects.component.scss',
})
export class AdminProjectsComponent implements OnInit {
  private contentService = inject(ContentService);
  private seo = inject(SeoService);
  private fb = inject(FormBuilder);

  projects = signal<Project[]>([]);
  loading = signal(true);
  editingId = signal<string | null>(null);
  saving = signal(false);
  formOpen = signal(false);

  form = this.fb.nonNullable.group({
    title: ['', Validators.required],
    slug: ['', Validators.required],
    summary: ['', Validators.required],
    problemStatement: [''],
    solution: [''],
    outcome: [''],
    coverImageUrl: [''],
    techStackText: [''],
    liveUrl: [''],
    repoUrl: [''],
    featured: [false],
    published: [true],
    displayOrder: [0],
  });

  ngOnInit(): void {
    this.seo.update({ title: 'Manage Portfolio', description: 'Neelastack portfolio CMS.', noindex: true });
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.contentService.listAllProjects().subscribe({
      next: (data) => {
        this.projects.set(data);
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
  }

  startCreate(): void {
    this.editingId.set(null);
    this.form.reset({
      title: '', slug: '', summary: '', problemStatement: '', solution: '', outcome: '',
      coverImageUrl: '', techStackText: '', liveUrl: '', repoUrl: '', featured: false, published: true, displayOrder: 0,
    });
    this.formOpen.set(true);
  }

  startEdit(project: Project): void {
    this.editingId.set(project.id);
    this.contentService.getProjectById(project.id).subscribe((full) => {
      this.form.setValue({
        title: full.title,
        slug: full.slug,
        summary: full.summary,
        problemStatement: full.problemStatement ?? '',
        solution: full.solution ?? '',
        outcome: full.outcome ?? '',
        coverImageUrl: full.coverImageUrl ?? '',
        techStackText: (full.techStack ?? []).join(', '),
        liveUrl: full.liveUrl ?? '',
        repoUrl: full.repoUrl ?? '',
        featured: full.featured,
        published: full.published ?? true,
        displayOrder: 0,
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
      techStack: raw.techStackText.split(',').map((t) => t.trim()).filter(Boolean),
    };
    delete (payload as any).techStackText;

    const editingId = this.editingId();
    const request = editingId
      ? this.contentService.updateProject(editingId, payload as any)
      : this.contentService.createProject(payload as any);

    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.load();
      },
      error: () => this.saving.set(false),
    });
  }

  remove(project: Project): void {
    if (!confirm(`Delete "${project.title}"? This can't be undone.`)) return;
    this.contentService.deleteProject(project.id).subscribe(() => this.load());
  }
}
