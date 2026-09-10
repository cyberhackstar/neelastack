import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BookingService } from '../../../core/services/booking.service';
import { SeoService } from '../../../core/services/seo.service';
import { AdminBookingDto, BookingDashboardStatsDto, BookingOutcome, BookingStatus } from '../../../core/models/booking.model';

/**
 * Admin bookings dashboard (master prompt sections 24-27): today/upcoming/month
 * counts + revenue, a filterable bookings table, and per-row status/outcome
 * actions. Kept as one page rather than a separate list/detail pair, matching the
 * scope of a single-operator business — see also AdminBookingController.
 */
@Component({
  selector: 'app-admin-bookings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-bookings.component.html',
  styleUrl: './admin-bookings.component.scss',
})
export class AdminBookingsComponent implements OnInit {
  private bookingService = inject(BookingService);
  private seo = inject(SeoService);

  stats = signal<BookingDashboardStatsDto | null>(null);
  bookings = signal<AdminBookingDto[]>([]);
  loading = signal(true);
  page = signal(0);
  totalPages = signal(0);
  statusFilter = signal<BookingStatus | ''>('');
  searchTerm = '';
  expandedId = signal<string | null>(null);

  readonly statusOptions: BookingStatus[] = ['SCHEDULED', 'CONFIRMED', 'RESCHEDULED', 'CANCELLED', 'COMPLETED', 'NO_SHOW'];
  readonly outcomeOptions: BookingOutcome[] = ['QUALIFIED', 'PROPOSAL_REQUESTED', 'FOLLOW_UP', 'NOT_A_FIT', 'WON', 'LOST'];

  ngOnInit(): void {
    this.seo.update({ title: 'Bookings', description: 'Manage Neelastack bookings.', noindex: true });
    this.bookingService.dashboardStats().subscribe({ next: (s) => this.stats.set(s) });
    this.loadBookings();
  }

  loadBookings(): void {
    this.loading.set(true);
    this.bookingService
      .listBookings({
        status: this.statusFilter() || undefined,
        search: this.searchTerm || undefined,
        page: this.page(),
        size: 20,
      })
      .subscribe({
        next: (result) => {
          this.bookings.set(result.content);
          this.totalPages.set(result.totalPages);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  onFilterChange(status: BookingStatus | ''): void {
    this.statusFilter.set(status);
    this.page.set(0);
    this.loadBookings();
  }

  onSearch(): void {
    this.page.set(0);
    this.loadBookings();
  }

  goToPage(delta: number): void {
    const next = this.page() + delta;
    if (next < 0 || next >= this.totalPages()) return;
    this.page.set(next);
    this.loadBookings();
  }

  toggleExpand(id: string): void {
    this.expandedId.set(this.expandedId() === id ? null : id);
  }

  markStatus(booking: AdminBookingDto, status: BookingStatus): void {
    this.bookingService.updateBookingStatus(booking.id, status).subscribe({
      next: (updated) => this.replaceBooking(updated),
    });
  }

  setOutcome(booking: AdminBookingDto, outcome: BookingOutcome, notes: string): void {
    this.bookingService.recordOutcome(booking.id, outcome, notes).subscribe({
      next: (updated) => this.replaceBooking(updated),
    });
  }

  private replaceBooking(updated: AdminBookingDto): void {
    this.bookings.set(this.bookings().map((b) => (b.id === updated.id ? updated : b)));
  }
}
