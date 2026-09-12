import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { BookingService } from '../../core/services/booking.service';
import { SeoService } from '../../core/services/seo.service';
import { DaySlotsDto, MeetingTypeDto, BookingDto } from '../../core/models/booking.model';

/**
 * The public, no-login booking page (master prompt section 6): pick a date, pick a
 * real available time (computed server-side by AvailabilityService), fill in the
 * meeting type's dynamic form fields, and confirm. Mirrors the flow the old
 * Calendly embed used to provide, but first-party — see BookingWidgetComponent /
 * InquiryService#resolveBookingUrl on the backend.
 */
@Component({
  selector: 'app-booking-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './booking-page.component.html',
  styleUrl: './booking-page.component.scss',
})
export class BookingPageComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private bookingService = inject(BookingService);
  private fb = inject(FormBuilder);
  private seo = inject(SeoService);

  meetingType = signal<MeetingTypeDto | null>(null);
  loading = signal(true);
  loadError = signal<string | null>(null);

  timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Kolkata';
  days = signal<DaySlotsDto[]>([]);
  selectedDate = signal<string | null>(null);
  selectedSlot = signal<string | null>(null);
  loadingSlots = signal(false);
  availabilityError = signal<string | null>(null);

  step = signal<'pick-time' | 'details' | 'confirmed'>('pick-time');
  submitting = signal(false);
  submitError = signal<string | null>(null);
  confirmation = signal<BookingDto | null>(null);

  detailsForm = this.fb.group({
    clientName: ['', [Validators.required, Validators.minLength(2)]],
    clientEmail: ['', [Validators.required, Validators.email]],
    clientPhone: [''],
    clientCompany: [''],
    inquiryId: [''],
  });
  dynamicFields: Record<string, string> = {};

  visibleDays = computed(() => this.days());

  ngOnInit(): void {
    const slug = this.route.snapshot.paramMap.get('slug');
    const inquiryId = this.route.snapshot.queryParamMap.get('inquiryId');
    if (!slug) {
      this.loadError.set('No meeting type specified.');
      this.loading.set(false);
      return;
    }
    if (inquiryId) {
      this.detailsForm.patchValue({ inquiryId });
    }

    this.bookingService.getMeetingType(slug).subscribe({
      next: (mt) => {
        this.meetingType.set(mt);
        this.seo.update({
          title: `Book: ${mt.name}`,
          description: mt.description || `Book a ${mt.name} with Neelastack.`,
          path: `/book/${mt.slug}`,
          noindex: false,
        });
        this.loading.set(false);
        this.loadAvailability(mt.slug);
      },
      error: () => {
        this.loadError.set("We couldn't find that meeting type. It may no longer be open for booking.");
        this.loading.set(false);
      },
    });
  }

  private loadAvailability(slug: string): void {
    this.loadingSlots.set(true);
    this.availabilityError.set(null);
    const from = new Date();
    const to = new Date();
    to.setDate(to.getDate() + (this.meetingType()?.maxHorizonDays ?? 30));
    const fmt = (date: Date, timezone: string) => {
      const parts = new Intl.DateTimeFormat('en-US', {
        timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit'
      }).formatToParts(date);
      const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
      return `${values['year']}-${values['month']}-${values['day']}`;
    };

    this.bookingService.getAvailability(slug, fmt(from, this.timezone), fmt(to, this.timezone), this.timezone).subscribe({
      next: (days) => {
        this.days.set(days);
        if (days.length > 0) {
          this.selectedDate.set(days[0].date);
        }
        this.loadingSlots.set(false);
      },
      error: (err) => {
        this.loadingSlots.set(false);
        this.availabilityError.set(
          err?.error?.message || 'We could not load availability right now. Please try again.',
        );
      },
    });
  }

  selectDate(date: string): void {
    this.selectedDate.set(date);
    this.selectedSlot.set(null);
  }

  slotsForSelectedDate(): string[] {
    const date = this.selectedDate();
    return this.days().find((d) => d.date === date)?.slots ?? [];
  }

  selectSlot(slot: string): void {
    this.selectedSlot.set(slot);
    this.step.set('details');
  }

  formatSlotTime(iso: string): string {
    return new Date(iso).toLocaleTimeString(undefined, {
      hour: 'numeric',
      minute: '2-digit',
      timeZone: this.timezone,
    });
  }

  googleCalendarUrl(booking: BookingDto): string {
    const toGoogleDate = (value: string): string =>
      new Date(value)
        .toISOString()
        .replace(/[-:]/g, '')
        .replace('.000Z', 'Z');

    const params = new URLSearchParams({
      action: 'TEMPLATE',
      text: booking.meetingTypeName + ' — Neelastack',
      dates: `${toGoogleDate(booking.startAt)}/${toGoogleDate(booking.endAt)}`,
      details: `Booking ${booking.bookingNumber}.${booking.meetingUrl ? `\nMeeting link: ${booking.meetingUrl}` : ''}`,
      location: booking.meetingUrl ?? '',
    });
    return `https://calendar.google.com/calendar/render?${params.toString()}`;
  }

  formatDayLabel(dateStr: string): string {
    const d = new Date(dateStr + 'T00:00:00');
    return d.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
  }

  onDynamicFieldChange(key: string, value: string): void {
    this.dynamicFields[key] = value;
  }

  submit(): void {
    if (this.detailsForm.invalid || !this.selectedSlot() || !this.meetingType()) {
      this.detailsForm.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.submitError.set(null);

    const inquiryId = (this.detailsForm.get('inquiryId')?.value as string | undefined) || undefined;
    const idempotencyKey = `${this.meetingType()!.slug}-${this.selectedSlot()}-${Date.now()}`;

    this.bookingService
      .createBooking({
        meetingTypeSlug: this.meetingType()!.slug,
        startAt: this.selectedSlot()!,
        clientTimezone: this.timezone,
        clientName: this.detailsForm.value.clientName!,
        clientEmail: this.detailsForm.value.clientEmail!,
        clientPhone: this.detailsForm.value.clientPhone || undefined,
        clientCompany: this.detailsForm.value.clientCompany || undefined,
        formResponses: this.dynamicFields,
        inquiryId,
        idempotencyKey,
        source: 'website',
        landingPage: typeof window !== 'undefined' ? window.location.href.slice(0, 300) : undefined,
        referrer: typeof document !== 'undefined' ? document.referrer.slice(0, 300) : undefined,
      })
      .subscribe({
        next: (booking) => {
          this.confirmation.set(booking);
          this.step.set('confirmed');
          this.submitting.set(false);
        },
        error: (err) => {
          this.submitError.set(
            err?.error?.message || 'That time may have just been taken. Please pick another slot.',
          );
          this.submitting.set(false);
          // The slot may no longer be valid — refresh availability so the person doesn't
          // retry against a stale list.
          this.loadAvailability(this.meetingType()!.slug);
          this.step.set('pick-time');
        },
      });
  }

  backToTimePicker(): void {
    this.step.set('pick-time');
  }
}






