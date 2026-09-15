import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { SeoService } from '../../core/services/seo.service';
import { InquiryService } from '../../core/services/inquiry.service';
import { AttributionService } from '../../core/services/attribution.service';
import { GaAnalyticsService } from '../../core/services/ga-analytics.service';
import { BusinessAuditFinding, BusinessAuditPreviewResult } from '../../core/models/content.model';
import { BookingWidgetComponent } from '../../shared/components/booking-widget/booking-widget.component';

const INDUSTRIES = [
  'Gym / fitness business', 'Restaurant / hospitality', 'Clinic / healthcare',
  'Retail / clothing store', 'Professional services', 'Startup / new venture', 'Other business',
];

const WEBSITE_OPTIONS = ['No website', 'Basic / outdated website', 'Modern website but weak conversion', 'Strong website'];
const ACTION_OPTIONS = ['Mostly offline', 'Information only', 'Booking / enquiry works', 'Customers can buy / book easily'];
const LEAD_OPTIONS = ['Manual / unclear', 'Basic contact form', 'Structured lead capture', 'Automated follow-up'];
const LOCAL_OPTIONS = ['Weak / unsure', 'Basic presence', 'Good presence', 'Strong local visibility'];
const GOALS = ['Get more customers', 'Sell online', 'Get more bookings / enquiries', 'Reduce manual work', 'Look more premium online', 'Launch a new digital product'];

@Component({
  selector: 'app-business-audit',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, BookingWidgetComponent],
  templateUrl: './business-audit.component.html',
  styleUrl: './business-audit.component.scss',
})
export class BusinessAuditComponent implements OnInit {
  private fb = inject(FormBuilder);
  private seo = inject(SeoService);
  private inquiryService = inject(InquiryService);
  private attribution = inject(AttributionService);
  private ga = inject(GaAnalyticsService);

  readonly industries = INDUSTRIES;
  readonly websiteOptions = WEBSITE_OPTIONS;
  readonly actionOptions = ACTION_OPTIONS;
  readonly leadOptions = LEAD_OPTIONS;
  readonly localOptions = LOCAL_OPTIONS;
  readonly goals = GOALS;

  step = signal<'form' | 'result' | 'unlock' | 'unlocked'>('form');
  scoring = signal(false);
  unlocking = signal(false);
  errorMessage = signal<string | null>(null);
  preview = signal<BusinessAuditPreviewResult | null>(null);
  findings = signal<BusinessAuditFinding[]>([]);
  recommendations = signal<string[]>([]);
  bookingUrl = signal<string | null>(null);

  form = this.fb.nonNullable.group({
    industry: ['', Validators.required],
    websitePresence: ['', Validators.required],
    customerAction: ['', Validators.required],
    leadCapture: ['', Validators.required],
    localDiscovery: ['', Validators.required],
    primaryGoal: ['', Validators.required],
  });

  unlockForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(180)]],
    phone: ['', [Validators.maxLength(20), Validators.pattern(/^[+0-9()\s.-]{7,20}$/)]],
    company: ['', [Validators.maxLength(120)]],
    website: ['', [Validators.maxLength(300)]],
    city: ['', [Validators.maxLength(80)]],
  });

  ngOnInit(): void {
    this.seo.update({
      title: 'Free Business Digital Audit',
      description: 'Get a free business-focused digital readiness score and see where your website, customer journey and online presence can improve.',
      path: '/free-business-audit',
    });
    this.ga.trackEvent('business_audit_start');
  }

  score(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.scoring.set(true);
    this.errorMessage.set(null);
    this.inquiryService.scoreBusinessAudit(this.form.getRawValue()).subscribe({
      next: (result) => { this.scoring.set(false); this.preview.set(result); this.step.set('result'); this.ga.trackEvent('business_audit_scored', { level: result.level }); },
      error: (err) => { this.scoring.set(false); this.errorMessage.set(err?.error?.message ?? 'We could not calculate your score. Please try again.'); },
    });
  }

  goToUnlock(): void { this.step.set('unlock'); this.ga.trackEvent('business_audit_unlock_start'); }

  unlock(): void {
    if (this.unlockForm.invalid) { this.unlockForm.markAllAsTouched(); return; }
    this.unlocking.set(true);
    this.errorMessage.set(null);
    this.inquiryService.unlockBusinessAudit({ ...this.form.getRawValue(), ...this.unlockForm.getRawValue(), ...this.attribution.get() }).subscribe({
      next: (result) => {
        this.unlocking.set(false);
        this.findings.set(result.findings);
        this.recommendations.set(result.recommendations);
        this.preview.set({ score: result.score, level: result.level, teaserFindings: [], lockedFindingsCount: 0, disclaimer: result.disclaimer });
        this.bookingUrl.set(result.inquiry.bookingUrl ?? null);
        this.step.set('unlocked');
        this.ga.trackEvent('business_audit_unlocked');
      },
      error: (err) => { this.unlocking.set(false); this.errorMessage.set(err?.error?.message ?? 'We could not unlock the report. Please try again.'); },
    });
  }

  hasError(name: 'industry' | 'websitePresence' | 'customerAction' | 'leadCapture' | 'localDiscovery' | 'primaryGoal'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }
}
