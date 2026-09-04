import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { SeoService } from '../../core/services/seo.service';
import { InquiryService } from '../../core/services/inquiry.service';
import { AttributionService } from '../../core/services/attribution.service';
import { GaAnalyticsService } from '../../core/services/ga-analytics.service';
import { BookingWidgetComponent } from '../../shared/components/booking-widget/booking-widget.component';

const CONCERN_OPTIONS = ['Performance', 'Security', 'Scalability', 'Technical debt', 'Not sure — general review'];

/**
 * "Already have an application?" free architecture review (master prompt section 22).
 * Deliberately a single short form, not a multi-step wizard like the estimator — the
 * whole point of a lead magnet is minimal friction.
 */
@Component({
  selector: 'app-architecture-review',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, BookingWidgetComponent],
  templateUrl: './architecture-review.component.html',
  styleUrl: './architecture-review.component.scss',
})
export class ArchitectureReviewComponent implements OnInit {
  private fb = inject(FormBuilder);
  private seo = inject(SeoService);
  private inquiryService = inject(InquiryService);
  private attribution = inject(AttributionService);
  private ga = inject(GaAnalyticsService);

  readonly concernOptions = CONCERN_OPTIONS;

  submitting = signal(false);
  submitted = signal(false);
  errorMessage = signal<string | null>(null);
  /** Module 2: set only when the created inquiry comes back Tier-1/HOT. */
  bookingUrl = signal<string | null>(null);

  form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    company: [''],
    applicationUrl: [''],
    currentStack: ['', [Validators.required, Validators.minLength(5)]],
    primaryConcerns: this.fb.nonNullable.array<string>([]),
    notes: [''],
  });

  ngOnInit(): void {
    this.seo.update({
      title: 'Free Architecture Review',
      description: 'Get a focused review of your existing application covering performance, security, scalability, and technical debt.',
      path: '/architecture-review',
    });
    this.ga.trackEvent('architecture_review_start');
  }

  toggleConcern(option: string, checked: boolean): void {
    const arr = this.form.controls.primaryConcerns;
    const idx = arr.value.indexOf(option);
    if (checked && idx === -1) {
      arr.push(this.fb.nonNullable.control(option));
    } else if (!checked && idx !== -1) {
      arr.removeAt(idx);
    }
  }

  isConcernChecked(option: string): boolean {
    return this.form.controls.primaryConcerns.value.includes(option);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const raw = this.form.getRawValue();
    const attribution = this.attribution.get();

    this.inquiryService
      .submitArchitectureReview({
        name: raw.name,
        email: raw.email,
        phone: raw.phone || undefined,
        company: raw.company || undefined,
        applicationUrl: raw.applicationUrl || undefined,
        currentStack: raw.currentStack,
        primaryConcerns: raw.primaryConcerns,
        notes: raw.notes || undefined,
        ...attribution,
      })
      .subscribe({
        next: (inquiry) => {
          this.submitting.set(false);
          this.submitted.set(true);
          this.bookingUrl.set(inquiry.bookingUrl ?? null);
          this.ga.trackEvent('architecture_review_submit');
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
