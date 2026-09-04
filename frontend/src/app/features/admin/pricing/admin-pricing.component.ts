import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { PricingService } from '../../../core/services/pricing.service';
import { PricingRule } from '../../../core/models/pricing.model';
import { SeoService } from '../../../core/services/seo.service';

/**
 * Admin CMS for the dynamic pricing engine (P0 fix): every base range and factor
 * the public estimator quotes lives in `pricing_rules`, edited here — never
 * hardcoded in application code. See EstimateCalculatorService on the backend.
 */
@Component({
  selector: 'app-admin-pricing',
  standalone: true,
  imports: [ReactiveFormsModule, DecimalPipe],
  templateUrl: './admin-pricing.component.html',
  styleUrl: './admin-pricing.component.scss',
})
export class AdminPricingComponent implements OnInit {
  private pricingService = inject(PricingService);
  private seo = inject(SeoService);
  private fb = inject(FormBuilder);

  rules = signal<PricingRule[]>([]);
  loading = signal(true);
  editingId = signal<string | null>(null);
  saving = signal(false);
  formOpen = signal(false);

  form = this.fb.nonNullable.group({
    serviceKey: ['', Validators.required],
    baseLow: [0, [Validators.required, Validators.min(0)]],
    baseHigh: this.fb.control<number | null>(null),
    complexityFactor: [0, [Validators.required, Validators.min(0)]],
    scaleFactor: [0, [Validators.required, Validators.min(0)]],
    integrationFactor: [0, [Validators.required, Validators.min(0)]],
    urgencyFactor: [0, [Validators.required, Validators.min(0)]],
    active: [true],
    notes: [''],
  });

  ngOnInit(): void {
    this.seo.update({ title: 'Manage Pricing Rules', description: 'Neelastack dynamic pricing engine.', noindex: true });
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.pricingService.listAll().subscribe({
      next: (data) => {
        this.rules.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  startCreate(): void {
    this.editingId.set(null);
    this.form.reset({
      serviceKey: '', baseLow: 0, baseHigh: null, complexityFactor: 0,
      scaleFactor: 0, integrationFactor: 0, urgencyFactor: 0, active: true, notes: '',
    });
    this.formOpen.set(true);
  }

  startEdit(rule: PricingRule): void {
    this.editingId.set(rule.id);
    this.form.setValue({
      serviceKey: rule.serviceKey,
      baseLow: rule.baseLow,
      baseHigh: rule.baseHigh,
      complexityFactor: rule.complexityFactor,
      scaleFactor: rule.scaleFactor,
      integrationFactor: rule.integrationFactor,
      urgencyFactor: rule.urgencyFactor,
      active: rule.active,
      notes: rule.notes ?? '',
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
      ? this.pricingService.update(editingId, payload)
      : this.pricingService.create(payload);

    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.load();
      },
      error: () => this.saving.set(false),
    });
  }

  remove(rule: PricingRule): void {
    if (!confirm(`Delete pricing rule "${rule.serviceKey}" (v${rule.version})? This can't be undone.`)) return;
    this.pricingService.delete(rule.id).subscribe(() => this.load());
  }
}
