import { Component, OnInit, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { SeoService } from '../../core/services/seo.service';
import { InquiryService } from '../../core/services/inquiry.service';
import { BookingWidgetComponent } from '../../shared/components/booking-widget/booking-widget.component';

// Maps the homepage "Build / Fix / Modernize" CTA to a sensible starting
// project type, so picking a path on the homepage actually saves the person
// a step here instead of just being a mood board.
const INTENT_PROJECT_TYPE: Record<string, string> = {
  build: 'New web application',
  fix: 'Existing app — fixes or features',
  modernize: 'Code audit / performance review',
};

@Component({
  selector: 'app-contact',
  standalone: true,
  imports: [ReactiveFormsModule, BookingWidgetComponent],
  templateUrl: './contact.component.html',
  styleUrl: './contact.component.scss',
})
export class ContactComponent implements OnInit {
  private fb = inject(FormBuilder);
  private seo = inject(SeoService);
  private inquiryService = inject(InquiryService);
  private route = inject(ActivatedRoute);

  submitting = signal(false);
  submitted = signal(false);
  errorMessage = signal<string | null>(null);
  /** Module 2: set only when the created inquiry comes back Tier-1/HOT. */
  bookingUrl = signal<string | null>(null);

  readonly projectTypes = [
    'New web application',
    'API / backend only',
    'Existing app — fixes or features',
    'Code audit / performance review',
    'Something else',
  ];

  readonly budgetRanges = [
    'Under ₹50,000',
    '₹50,000 – ₹2,00,000',
    '₹2,00,000 – ₹5,00,000',
    '₹5,00,000+',
    'Not sure yet',
  ];

  readonly nextSteps = [
    { title: 'You submit', description: 'A short brief — what you\'re building, timeline, budget range.' },
    { title: 'I reply within a business day', description: 'With clarifying questions if needed, or a scoped estimate directly.' },
    { title: 'You get a written proposal', description: 'Fixed price, fixed scope, clear timeline — before anything is built.' },
    { title: 'We start', description: 'Tracked on your own project dashboard from day one.' },
  ];

  form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    company: [''],
    projectType: [''],
    budgetRange: [''],
    message: ['', [Validators.required, Validators.minLength(20)]],
  });

  ngOnInit(): void {
    this.seo.update({
      title: 'Contact',
      description: "Start a project with Neelastack — tell me what you're building and get a scoped estimate.",
      path: '/contact',
    });

    const intent = this.route.snapshot.queryParamMap.get('intent');
    const projectType = intent ? INTENT_PROJECT_TYPE[intent] : undefined;
    if (projectType) {
      this.form.patchValue({ projectType });
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    this.inquiryService.submitInquiry(this.form.getRawValue()).subscribe({
      next: (inquiry) => {
        this.submitting.set(false);
        this.submitted.set(true);
        this.bookingUrl.set(inquiry.bookingUrl ?? null);
      },
      error: (err) => {
        this.submitting.set(false);
        this.errorMessage.set(err?.error?.message ?? 'Something went wrong. Please try again or email hello@neelastack.com directly.');
      },
    });
  }
}
