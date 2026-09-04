import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { SeoService } from '../../core/services/seo.service';
import { InquiryService } from '../../core/services/inquiry.service';
import { AttributionService } from '../../core/services/attribution.service';
import { GaAnalyticsService } from '../../core/services/ga-analytics.service';
import { AuditFinding, AuditPreviewResult } from '../../core/models/content.model';
import { BookingWidgetComponent } from '../../shared/components/booking-widget/booking-widget.component';

const TECH_STACK_OPTIONS = ['Spring Boot', 'Angular', 'PostgreSQL', 'Redis', 'React', 'Node.js', 'MySQL', 'MongoDB'];

const BOTTLENECK_OPTIONS: { key: string; label: string }[] = [
  { key: 'DB_CONNECTION_POOLING', label: 'Database connection pooling under load' },
  { key: 'SSR_HYDRATION_DELAY', label: 'Slow SSR hydration / time-to-interactive' },
  { key: 'PAYMENT_CONCURRENCY', label: 'Payment concurrency / race conditions' },
  { key: 'N_PLUS_ONE_QUERIES', label: 'N+1 queries on list/dashboard pages' },
  { key: 'CACHE_INVALIDATION', label: 'Stale or inconsistent cache data' },
  { key: 'JWT_REFRESH_RACE', label: 'Auth/session token refresh races' },
  { key: 'WEBHOOK_IDEMPOTENCY', label: 'Duplicate webhook processing' },
  { key: 'AUTH_RATE_LIMITING', label: 'No rate limiting on login/OTP endpoints' },
];

/**
 * Module 1 of the Client Acquisition & High-Ticket Conversion Engine: the
 * "Instant Architecture Risk Score" lead magnet at /audit-preview.
 *
 * Two-step, no-friction-first UX: step 1 (pick stack + check concerns) requires
 * no contact info and calls the free /audit-preview/score endpoint for an instant
 * score + two teaser findings; step 2 gates the full breakdown behind name/email/
 * company via /audit-preview/unlock, which is what actually creates a lead.
 */
@Component({
  selector: 'app-audit-preview',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, BookingWidgetComponent],
  templateUrl: './audit-preview.component.html',
  styleUrl: './audit-preview.component.scss',
})
export class AuditPreviewComponent implements OnInit {
  private fb = inject(FormBuilder);
  private seo = inject(SeoService);
  private inquiryService = inject(InquiryService);
  private attribution = inject(AttributionService);
  private ga = inject(GaAnalyticsService);

  readonly techStackOptions = TECH_STACK_OPTIONS;
  readonly bottleneckOptions = BOTTLENECK_OPTIONS;

  step = signal<'select' | 'preview' | 'unlock' | 'unlocked'>('select');
  scoring = signal(false);
  unlocking = signal(false);
  errorMessage = signal<string | null>(null);

  preview = signal<AuditPreviewResult | null>(null);
  findings = signal<AuditFinding[]>([]);
  bookingUrl = signal<string | null>(null);

  selectionForm = this.fb.nonNullable.group({
    techStack: this.fb.nonNullable.array<string>([], Validators.required),
    bottlenecks: this.fb.nonNullable.array<string>([], Validators.required),
  });

  unlockForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    company: ['', [Validators.required, Validators.minLength(2)]],
  });

  ngOnInit(): void {
    this.seo.update({
      title: 'Instant Architecture Risk Score',
      description: 'Select your stack and flag common scaling bottlenecks for an instant, free risk score — then unlock the full breakdown.',
      path: '/audit-preview',
    });
    this.ga.trackEvent('audit_preview_start');
  }

  toggle(arrayName: 'techStack' | 'bottlenecks', value: string, checked: boolean): void {
    const arr = this.selectionForm.controls[arrayName];
    const idx = arr.value.indexOf(value);
    if (checked && idx === -1) {
      arr.push(this.fb.nonNullable.control(value));
    } else if (!checked && idx !== -1) {
      arr.removeAt(idx);
    }
  }

  isChecked(arrayName: 'techStack' | 'bottlenecks', value: string): boolean {
    return this.selectionForm.controls[arrayName].value.includes(value);
  }

  getScore(): void {
    if (this.selectionForm.invalid) {
      this.selectionForm.markAllAsTouched();
      return;
    }
    this.scoring.set(true);
    this.errorMessage.set(null);

    const raw = this.selectionForm.getRawValue();
    this.inquiryService.scoreAuditPreview({ techStack: raw.techStack, bottlenecks: raw.bottlenecks }).subscribe({
      next: (result) => {
        this.scoring.set(false);
        this.preview.set(result);
        this.step.set('preview');
        this.ga.trackEvent('audit_preview_scored', { riskLevel: result.riskLevel });
      },
      error: (err) => {
        this.scoring.set(false);
        this.errorMessage.set(err?.error?.message ?? 'Something went wrong scoring your stack. Please try again.');
      },
    });
  }

  goToUnlock(): void {
    this.step.set('unlock');
  }

  unlock(): void {
    if (this.unlockForm.invalid) {
      this.unlockForm.markAllAsTouched();
      return;
    }
    this.unlocking.set(true);
    this.errorMessage.set(null);

    const raw = this.unlockForm.getRawValue();
    const selection = this.selectionForm.getRawValue();
    const attribution = this.attribution.get();

    this.inquiryService
      .unlockAuditPreview({
        techStack: selection.techStack,
        bottlenecks: selection.bottlenecks,
        name: raw.name,
        email: raw.email,
        phone: raw.phone || undefined,
        company: raw.company,
        ...attribution,
      })
      .subscribe({
        next: (result) => {
          this.unlocking.set(false);
          this.findings.set(result.findings);
          this.preview.set({
            riskScore: result.riskScore,
            riskLevel: result.riskLevel as AuditPreviewResult['riskLevel'],
            teaserFindings: [],
            lockedFindingsCount: 0,
            disclaimer: result.disclaimer,
          });
          this.bookingUrl.set(result.inquiry.bookingUrl ?? null);
          this.step.set('unlocked');
          this.ga.trackEvent('audit_preview_unlocked');
        },
        error: (err) => {
          this.unlocking.set(false);
          this.errorMessage.set(
            err?.error?.message ?? 'Something went wrong. Please try again or email hello@neelastack.com directly.',
          );
        },
      });
  }
}
