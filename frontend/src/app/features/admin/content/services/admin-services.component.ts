import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ContentService } from '../../../../core/services/content.service';
import { ServiceItem } from '../../../../core/models/content.model';
import { SeoService } from '../../../../core/services/seo.service';

@Component({
  selector: 'app-admin-services',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './admin-services.component.html',
  styleUrl: './admin-services.component.scss',
})
export class AdminServicesComponent implements OnInit {
  private contentService = inject(ContentService);
  private seo = inject(SeoService);
  private fb = inject(FormBuilder);

  services = signal<ServiceItem[]>([]);
  loading = signal(true);
  editingId = signal<string | null>(null);
  saving = signal(false);
  formOpen = signal(false);

  form = this.fb.nonNullable.group({
    title: ['', Validators.required],
    slug: ['', Validators.required],
    summary: ['', Validators.required],
    description: [''],
    icon: [''],
    startingPrice: [''],
    displayOrder: [0],
    published: [true],
  });

  ngOnInit(): void {
    this.seo.update({ title: 'Manage Services', description: 'Neelastack services CMS.', noindex: true });
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.contentService.listAllServices().subscribe({
      next: (data) => {
        this.services.set(data);
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
    this.form.reset({ title: '', slug: '', summary: '', description: '', icon: '', startingPrice: '', displayOrder: 0, published: true });
    this.formOpen.set(true);
  }

  startEdit(service: ServiceItem): void {
    this.editingId.set(service.id);
    this.form.setValue({
      title: service.title,
      slug: service.slug,
      summary: service.summary,
      description: service.description ?? '',
      icon: service.icon ?? '',
      startingPrice: service.startingPrice ?? '',
      displayOrder: service.displayOrder,
      published: true,
    });
    this.contentService.getServiceById(service.id).subscribe((full) => {
      this.form.patchValue({ published: full.published ?? true });
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
    const payload = this.form.getRawValue();
    const editingId = this.editingId();

    const request = editingId
      ? this.contentService.updateService(editingId, payload)
      : this.contentService.createService(payload);

    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.load();
      },
      error: () => this.saving.set(false),
    });
  }

  remove(service: ServiceItem): void {
    if (!confirm(`Delete "${service.title}"? This can't be undone.`)) return;
    this.contentService.deleteService(service.id).subscribe(() => this.load());
  }
}
