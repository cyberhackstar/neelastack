import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { SeoService } from '../../core/services/seo.service';
import { TestimonialService } from '../../core/services/testimonial.service';
import { TestimonialRequestPublic } from '../../core/models/content.model';

/**
 * Module 4 of the Client Acquisition & High-Ticket Conversion Engine: the
 * client-facing side of the post-invoice testimonial loop, reached via a
 * one-time link emailed automatically once an invoice is confirmed PAID.
 */
@Component({
  selector: 'app-testimonial',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './testimonial.component.html',
  styleUrl: './testimonial.component.scss',
})
export class TestimonialComponent implements OnInit {
  private fb = inject(FormBuilder);
  private route = inject(ActivatedRoute);
  private seo = inject(SeoService);
  private testimonialService = inject(TestimonialService);

  loading = signal(true);
  notFound = signal(false);
  request = signal<TestimonialRequestPublic | null>(null);
  submitting = signal(false);
  submitted = signal(false);
  errorMessage = signal<string | null>(null);

  private token = '';

  readonly ratingOptions = [5, 4, 3, 2, 1];

  form = this.fb.nonNullable.group({
    rating: [5, [Validators.required]],
    authorTitle: [''],
    reviewBody: ['', [Validators.required, Validators.minLength(20)]],
    videoUrl: [''],
  });

  ngOnInit(): void {
    this.seo.update({
      title: 'Share Your Feedback',
      description: 'Leave a quick review of your project with Neelastack.',
      path: '/testimonial',
      noindex: true,
    });

    this.token = this.route.snapshot.paramMap.get('token') ?? '';
    if (!this.token) {
      this.notFound.set(true);
      this.loading.set(false);
      return;
    }

    this.testimonialService.get(this.token).subscribe({
      next: (data) => {
        this.request.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.notFound.set(true);
        this.loading.set(false);
      },
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    const raw = this.form.getRawValue();
    this.testimonialService
      .submit(this.token, {
        rating: raw.rating,
        authorTitle: raw.authorTitle || undefined,
        reviewBody: raw.reviewBody,
        videoUrl: raw.videoUrl || undefined,
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.submitted.set(true);
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
