import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { BookingService } from '../../core/services/booking.service';
import { BookingDto, DaySlotsDto } from '../../core/models/booking.model';

/**
 * The no-login "view / reschedule / cancel my booking" page reached from the
 * confirmation/reminder emails via a secure token — never a raw booking id. See
 * master prompt sections 15, 17, 18.
 */
@Component({
  selector: 'app-booking-manage',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './booking-manage.component.html',
  styleUrl: './booking-manage.component.scss',
})
export class BookingManageComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private bookingService = inject(BookingService);

  booking = signal<BookingDto | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);

  mode = signal<'view' | 'reschedule' | 'cancel-confirm'>('view');
  rescheduleDays = signal<DaySlotsDto[]>([]);
  rescheduleSelectedDate = signal<string | null>(null);
  actionInFlight = signal(false);
  actionError = signal<string | null>(null);
  cancelReason = '';

  ngOnInit(): void {
    const token = this.route.snapshot.paramMap.get('token');
    if (!token) {
      this.error.set('Invalid booking link.');
      this.loading.set(false);
      return;
    }
    this.load(token);
  }

  private load(token: string): void {
    this.bookingService.getBookingByToken(token).subscribe({
      next: (b) => {
        this.booking.set(b);
        this.loading.set(false);
      },
      error: () => {
        this.error.set("We couldn't find that booking. The link may be invalid or expired.");
        this.loading.set(false);
      },
    });
  }

  formatDateTime(iso: string, timezone: string): string {
    return new Date(iso).toLocaleString(undefined, {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      hour: 'numeric',
      minute: '2-digit',
      timeZone: timezone,
      timeZoneName: 'short',
    });
  }

  formatSlotTime(iso: string): string {
    return new Date(iso).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
  }

  formatDayLabel(dateStr: string): string {
    const d = new Date(dateStr + 'T00:00:00');
    return d.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
  }

  startReschedule(): void {
    this.mode.set('reschedule');
    this.actionError.set(null);
    const b = this.booking();
    if (!b) return;
    this.bookingService.getAvailability(b.meetingTypeSlug, this.todayIso(b.clientTimezone), this.plusDaysIso(45, b.clientTimezone), b.clientTimezone)
      .subscribe({
        next: (days) => {
          this.rescheduleDays.set(days);
          if (days.length > 0) this.rescheduleSelectedDate.set(days[0].date);
        },
        error: () => {
          this.actionError.set('Unable to load available times right now.');
        },
      });
  }

  private todayIso(timezone: string): string {
    return this.isoDateInTimezone(new Date(), timezone);
  }

  private plusDaysIso(days: number, timezone: string): string {
    const d = new Date();
    d.setDate(d.getDate() + days);
    return this.isoDateInTimezone(d, timezone);
  }

  private isoDateInTimezone(date: Date, timezone: string): string {
    const parts = new Intl.DateTimeFormat('en-US', {
      timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit'
    }).formatToParts(date);
    const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
    return `${values['year']}-${values['month']}-${values['day']}`;
  }

  selectRescheduleDate(date: string): void {
    this.rescheduleSelectedDate.set(date);
  }

  slotsForRescheduleDate(): string[] {
    return this.rescheduleDays().find((d) => d.date === this.rescheduleSelectedDate())?.slots ?? [];
  }

  confirmReschedule(newStartAt: string): void {
    const b = this.booking();
    if (!b) return;
    this.actionInFlight.set(true);
    this.bookingService.rescheduleBooking(b.rescheduleToken, newStartAt, b.clientTimezone).subscribe({
      next: (updated) => {
        this.booking.set(updated);
        this.mode.set('view');
        this.actionInFlight.set(false);
      },
      error: (err) => {
        this.actionError.set(err?.error?.message || 'That time is no longer available. Please pick another.');
        this.actionInFlight.set(false);
        this.startReschedule();
      },
    });
  }

  startCancel(): void {
    this.mode.set('cancel-confirm');
    this.actionError.set(null);
  }

  backToView(): void {
    this.mode.set('view');
  }

  confirmCancel(): void {
    const b = this.booking();
    if (!b) return;
    this.actionInFlight.set(true);
    this.bookingService.cancelBooking(b.cancelToken, this.cancelReason || undefined).subscribe({
      next: (updated) => {
        this.booking.set(updated);
        this.mode.set('view');
        this.actionInFlight.set(false);
      },
      error: (err) => {
        this.actionError.set(err?.error?.message || 'Unable to cancel this booking.');
        this.actionInFlight.set(false);
      },
    });
  }
}
