import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { SeoService } from '../../core/services/seo.service';
import { InquiryService } from '../../core/services/inquiry.service';
import { AttributionService } from '../../core/services/attribution.service';
import { GaAnalyticsService } from '../../core/services/ga-analytics.service';
import { Estimate, InquiryIntent } from '../../core/models/content.model';
import { BookingWidgetComponent } from '../../shared/components/booking-widget/booking-widget.component';

interface StepDef {
  key: string;
  label: string;
}

const INTENT_PROJECT_TYPES: Record<InquiryIntent, string[]> = {
  BUILD: ['New web application', 'API / backend only', 'Business platform / SaaS', 'E-commerce', 'Something else'],
  FIX: ['Existing app — fixes or features', 'API / backend only', 'Performance issue', 'Something else'],
  MODERNIZE: ['Code audit / performance review', 'Legacy modernization', 'Cloud migration', 'Something else'],
  AUDIT: ['Architecture review', 'Security review', 'Performance & scalability review', 'Something else'],
  GENERAL: ['New web application', 'Something else'],
};

const INTEGRATION_OPTIONS = ['Payments (Razorpay/Stripe)', 'Google/social login', 'File storage (Cloudinary/S3)', 'Email/SMS notifications', 'Third-party API', 'None yet'];

/**
 * Multi-step project estimator (master prompt section 21). All steps are collected in
 * one reactive form; "steps" just control which fields are visible/required at a time.
 * The whole thing submits once, at the final step, to POST /public/estimator.
 */
@Component({
  selector: 'app-estimator',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, DecimalPipe, BookingWidgetComponent],
  templateUrl: './estimator.component.html',
  styleUrl: './estimator.component.scss',
})
export class EstimatorComponent implements OnInit {
  private fb = inject(FormBuilder);
  private seo = inject(SeoService);
  private inquiryService = inject(InquiryService);
  private attribution = inject(AttributionService);
  private route = inject(ActivatedRoute);
  private ga = inject(GaAnalyticsService);

  readonly integrationOptions = INTEGRATION_OPTIONS;
  readonly usersScaleOptions = ['Just me / a few users', 'Hundreds of users', 'Thousands of users', 'Not sure yet'];
  readonly timelineOptions = ['ASAP (production issue)', 'Within 1 month', '1–3 months', '3–6 months', 'Not sure yet'];
  readonly urgencyOptions = ['Production is down', 'Important, not urgent', 'Planning ahead'];
  readonly budgetOptions = ['Under ₹50,000', '₹50,000 – ₹2,00,000', '₹2,00,000 – ₹5,00,000', '₹5,00,000+', 'Not sure yet'];

  stepIndex = signal(0);
  submitting = signal(false);
  submitted = signal(false);
  errorMessage = signal<string | null>(null);
  estimate = signal<Estimate | null>(null);
  /** Module 2: set only when the created inquiry comes back Tier-1/HOT. */
  bookingUrl = signal<string | null>(null);

  form = this.fb.nonNullable.group({
    intent: ['BUILD' as InquiryIntent, Validators.required],
    projectType: ['', Validators.required],
    existingSystem: [''],
    scopeDetails: ['', [Validators.required, Validators.minLength(15)]],
    usersScale: [''],
    integrations: this.fb.nonNullable.array<string>([]),
    timeline: ['', Validators.required],
    urgency: [''],
    budgetRange: ['', Validators.required],
    name: ['', [Validators.required, Validators.minLength(2)]],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    company: [''],
  });

  projectTypeOptions = computed(() => INTENT_PROJECT_TYPES[this.form.controls.intent.value] ?? INTENT_PROJECT_TYPES['GENERAL']);

  steps = computed<StepDef[]>(() => {
    const base: StepDef[] = [{ key: 'intent', label: 'Project intent' }, { key: 'type', label: 'Project type' }];
    if (this.form.controls.intent.value !== 'BUILD') {
      base.push({ key: 'existing', label: 'Existing system' });
    }
    base.push(
      { key: 'scope', label: 'Scope' },
      { key: 'scale', label: 'Users & integrations' },
      { key: 'timeline', label: 'Timeline & budget' },
      { key: 'contact', label: 'Contact details' },
      { key: 'estimate', label: 'Preliminary estimate' },
    );
    return base;
  });

  currentStepKey = computed(() => this.steps()[this.stepIndex()]?.key ?? 'intent');
  isLastInputStep = computed(() => this.stepIndex() === this.steps().length - 2);

  ngOnInit(): void {
    this.seo.update({
      title: 'Project Estimator',
      description: 'Tell us what you\u2019re building and get a preliminary estimate in a few steps — no sales call required.',
      path: '/estimate',
    });

    const intent = this.route.snapshot.queryParamMap.get('intent')?.toUpperCase();
    if (intent === 'BUILD' || intent === 'FIX' || intent === 'MODERNIZE') {
      this.form.patchValue({ intent });
      if (intent === 'FIX') {
        this.form.patchValue({ urgency: this.urgencyOptions[0] });
      }
    }

    this.ga.trackEvent('estimator_start', { intent: this.form.controls.intent.value });
  }

  selectIntent(intent: InquiryIntent): void {
    this.form.patchValue({ intent, projectType: '' });
  }

  toggleIntegration(option: string, checked: boolean): void {
    const arr = this.form.controls.integrations;
    const idx = arr.value.indexOf(option);
    if (checked && idx === -1) {
      arr.push(this.fb.nonNullable.control(option));
    } else if (!checked && idx !== -1) {
      arr.removeAt(idx);
    }
  }

  isIntegrationChecked(option: string): boolean {
    return this.form.controls.integrations.value.includes(option);
  }

  canAdvance(): boolean {
    switch (this.currentStepKey()) {
      case 'intent':
        return !!this.form.controls.intent.value;
      case 'type':
        return this.form.controls.projectType.valid;
      case 'existing':
        return true; // optional detail — never blocks progress
      case 'scope':
        return this.form.controls.scopeDetails.valid;
      case 'scale':
        return true; // optional detail — never blocks progress
      case 'timeline':
        return this.form.controls.timeline.valid && this.form.controls.budgetRange.valid;
      case 'contact':
        return this.form.controls.name.valid && this.form.controls.email.valid;
      default:
        return true;
    }
  }

  next(): void {
    if (!this.canAdvance()) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.isLastInputStep()) {
      this.submit();
      return;
    }
    this.stepIndex.update((i) => Math.min(i + 1, this.steps().length - 1));
  }

  back(): void {
    this.stepIndex.update((i) => Math.max(i - 1, 0));
  }

  private submit(): void {
    this.submitting.set(true);
    this.errorMessage.set(null);

    const raw = this.form.getRawValue();
    const attribution = this.attribution.get();

    this.inquiryService
      .submitEstimate({
        intent: raw.intent,
        projectType: raw.projectType,
        existingSystem: raw.existingSystem || undefined,
        scopeDetails: raw.scopeDetails,
        usersScale: raw.usersScale || undefined,
        integrations: raw.integrations,
        timeline: raw.timeline,
        urgency: raw.urgency || undefined,
        budgetRange: raw.budgetRange,
        name: raw.name,
        email: raw.email,
        phone: raw.phone || undefined,
        company: raw.company || undefined,
        ...attribution,
      })
      .subscribe({
        next: (res) => {
          this.submitting.set(false);
          this.submitted.set(true);
          this.estimate.set(res.estimate);
          this.bookingUrl.set(res.inquiry.bookingUrl ?? null);
          this.stepIndex.set(this.steps().length - 1);
          this.ga.trackEvent('estimator_complete', { intent: raw.intent });
        },
        error: (err) => {
          this.submitting.set(false);
          this.errorMessage.set(
            err?.error?.message ?? 'Something went wrong. Please try again or email hello@neelastack.com directly.',
          );
        },
      });
  }
}
