import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import {
  AdminBookingDto,
  AvailabilityOverrideDto,
  AvailabilityWindowDto,
  BookingDashboardStatsDto,
  BookingDto,
  BookingFunnelStatsDto,
  BookingOutcome,
  BookingRequestPayload,
  BookingStatus,
  DaySlotsDto,
  MeetingTypeDto,
  RevenueBySourceDto,
} from '../models/booking.model';

// Page is defined in content.model.ts already; re-export shape here to avoid a
// circular import if that changes — see core/models/content.model.ts#Page.
type PageOf<T> = { content: T[]; totalElements: number; totalPages: number; number: number };

@Injectable({ providedIn: 'root' })
export class BookingService {
  private http = inject(HttpClient);
  private readonly publicBase = `${environment.apiBaseUrl}/public/booking`;
  private readonly adminBase = `${environment.apiBaseUrl}/admin/booking`;

  // --- Public -------------------------------------------------------------------

  listMeetingTypes() {
    return this.http.get<MeetingTypeDto[]>(`${this.publicBase}/meeting-types`);
  }

  getMeetingType(slug: string) {
    return this.http.get<MeetingTypeDto>(`${this.publicBase}/meeting-types/${slug}`);
  }

  getAvailability(slug: string, from: string, to: string, timezone: string) {
    const params = new HttpParams().set('slug', slug).set('from', from).set('to', to).set('timezone', timezone);
    return this.http.get<DaySlotsDto[]>(`${this.publicBase}/availability`, { params });
  }

  createBooking(payload: BookingRequestPayload) {
    return this.http.post<BookingDto>(`${this.publicBase}/bookings`, payload);
  }

  getBookingByToken(viewToken: string) {
    return this.http.get<BookingDto>(`${this.publicBase}/bookings/${viewToken}`);
  }

  rescheduleBooking(rescheduleToken: string, newStartAt: string, clientTimezone: string) {
    return this.http.post<BookingDto>(`${this.publicBase}/bookings/${rescheduleToken}/reschedule`, {
      newStartAt,
      clientTimezone,
    });
  }

  cancelBooking(cancelToken: string, reason?: string) {
    return this.http.post<BookingDto>(`${this.publicBase}/bookings/${cancelToken}/cancel`, { reason });
  }

  // --- Admin --------------------------------------------------------------------

  listMeetingTypesAdmin() {
    return this.http.get<MeetingTypeDto[]>(`${this.adminBase}/meeting-types`);
  }

  createMeetingType(payload: unknown) {
    return this.http.post<MeetingTypeDto>(`${this.adminBase}/meeting-types`, payload);
  }

  updateMeetingType(id: string, payload: unknown) {
    return this.http.put<MeetingTypeDto>(`${this.adminBase}/meeting-types/${id}`, payload);
  }

  deleteMeetingType(id: string) {
    return this.http.delete<void>(`${this.adminBase}/meeting-types/${id}`);
  }

  listBookings(params: { status?: BookingStatus; meetingTypeId?: string; search?: string; page?: number; size?: number }) {
    let httpParams = new HttpParams();
    if (params.status) httpParams = httpParams.set('status', params.status);
    if (params.meetingTypeId) httpParams = httpParams.set('meetingTypeId', params.meetingTypeId);
    if (params.search) httpParams = httpParams.set('search', params.search);
    httpParams = httpParams.set('page', params.page ?? 0).set('size', params.size ?? 20);
    return this.http.get<PageOf<AdminBookingDto>>(`${this.adminBase}/bookings`, { params: httpParams });
  }

  getBookingAdmin(id: string) {
    return this.http.get<AdminBookingDto>(`${this.adminBase}/bookings/${id}`);
  }

  updateBookingStatus(id: string, status: BookingStatus) {
    return this.http.patch<AdminBookingDto>(`${this.adminBase}/bookings/${id}/status`, { status });
  }

  recordOutcome(id: string, outcome: BookingOutcome, internalNotes?: string) {
    return this.http.patch<AdminBookingDto>(`${this.adminBase}/bookings/${id}/outcome`, { outcome, internalNotes });
  }

  dashboardStats() {
    return this.http.get<BookingDashboardStatsDto>(`${this.adminBase}/dashboard`);
  }

  funnelStats(from?: string, to?: string) {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<BookingFunnelStatsDto>(`${this.adminBase}/analytics/funnel`, { params });
  }

  revenueBySource(from?: string, to?: string) {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<RevenueBySourceDto[]>(`${this.adminBase}/analytics/revenue-by-source`, { params });
  }

  listAvailabilityWindows() {
    return this.http.get<AvailabilityWindowDto[]>(`${this.adminBase}/availability/windows`);
  }

  addAvailabilityWindow(dayOfWeek: number, startTime: string, endTime: string, timezone = 'Asia/Kolkata') {
    return this.http.post<AvailabilityWindowDto>(`${this.adminBase}/availability/windows`, {
      dayOfWeek,
      startTime,
      endTime,
      timezone,
    });
  }

  removeAvailabilityWindow(id: string) {
    return this.http.delete<void>(`${this.adminBase}/availability/windows/${id}`);
  }

  listAvailabilityOverrides(from: string, to: string) {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<AvailabilityOverrideDto[]>(`${this.adminBase}/availability/overrides`, { params });
  }

  removeAvailabilityOverride(date: string) {
    return this.http.delete<void>(`${this.adminBase}/availability/overrides/${date}`);
  }

  upsertAvailabilityOverride(overrideDate: string, isAvailable: boolean, startTime?: string, endTime?: string, reason?: string) {
    return this.http.post<AvailabilityOverrideDto>(`${this.adminBase}/availability/overrides`, {
      overrideDate,
      isAvailable,
      startTime,
      endTime,
      reason,
    });
  }

  calendarStatus() {
    return this.http.get<{ featureEnabled: boolean; connected: boolean }>(`${this.adminBase}/calendar/status`);
  }

  connectCalendar() {
    return this.http.post<{ authorizationUrl: string }>(`${this.adminBase}/calendar/connect`, {});
  }

  disconnectCalendar() {
    return this.http.post<void>(`${this.adminBase}/calendar/disconnect`, {});
  }
}
