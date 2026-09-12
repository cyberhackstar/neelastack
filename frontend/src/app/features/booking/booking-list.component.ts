import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BookingService } from '../../core/services/booking.service';
import { SeoService } from '../../core/services/seo.service';
import { MeetingTypeDto } from '../../core/models/booking.model';

@Component({
  selector: 'app-booking-list',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './booking-list.component.html',
  styleUrl: './booking-list.component.scss',
})
export class BookingListComponent implements OnInit {
  private readonly bookingService = inject(BookingService);
  private readonly seo = inject(SeoService);

  readonly meetingTypes = signal<MeetingTypeDto[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.seo.update({
      title: 'Schedule a consultation',
      description:
        'Choose a consultation with Neelastack, see available times, and book a conversation without creating an account.',
      path: '/book',
      noindex: false,
    });

    this.bookingService.listMeetingTypes().subscribe({
      next: (types) => {
        this.meetingTypes.set((types ?? []).filter((type) => type?.isActive));
        this.loading.set(false);
      },
      error: () => {
        this.error.set('We could not load the available consultations. Please try again shortly.');
        this.loading.set(false);
      },
    });
  }

  formatPrice(type: MeetingTypeDto): string {
    if (!type.price) return 'Free';
    return `${type.currency} ${type.price}`;
  }
}
