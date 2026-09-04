import { Component, Input } from '@angular/core';

/**
 * Module 2 of the Client Acquisition & High-Ticket Conversion Engine: shown in
 * place of a generic "thank you" message whenever an inquiry response comes back
 * with a bookingUrl (Tier-1/HOT leads only — see LeadScoringService#isTierOne and
 * InquiryService#resolveBookingUrl on the backend). A plain link-out rather than
 * a full embedded iframe widget, so it works the same with Calendly or any other
 * scheduling tool without extra script-loading/CSP complexity.
 */
@Component({
  selector: 'app-booking-widget',
  standalone: true,
  templateUrl: './booking-widget.component.html',
  styleUrl: './booking-widget.component.scss',
})
export class BookingWidgetComponent {
  @Input({ required: true }) bookingUrl!: string;
}
