// Booking engine models — mirror backend com.neelastack.dto.booking DTOs.

export type LocationType = 'GOOGLE_MEET' | 'ZOOM' | 'TEAMS' | 'PHONE' | 'IN_PERSON' | 'CUSTOM';
export type FormFieldType =
  | 'TEXT'
  | 'TEXTAREA'
  | 'EMAIL'
  | 'PHONE'
  | 'NUMBER'
  | 'SELECT'
  | 'MULTI_SELECT'
  | 'RADIO'
  | 'CHECKBOX'
  | 'URL'
  | 'DATE';
export type BookingStatus = 'SCHEDULED' | 'CONFIRMED' | 'RESCHEDULED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW';
export type BookingOutcome = 'QUALIFIED' | 'PROPOSAL_REQUESTED' | 'FOLLOW_UP' | 'NOT_A_FIT' | 'WON' | 'LOST';
export type BookingPaymentStatus = 'NOT_REQUIRED' | 'PENDING' | 'PAID' | 'FAILED' | 'REFUNDED';

export interface FormFieldDto {
  fieldKey: string;
  label: string;
  fieldType: FormFieldType;
  isRequired: boolean;
  options: string[] | null;
  sortOrder: number;
}

export interface MeetingTypeDto {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  durationMinutes: number;
  bufferBeforeMinutes: number;
  bufferAfterMinutes: number;
  minNoticeMinutes: number;
  maxHorizonDays: number;
  locationType: LocationType;
  locationDetail: string | null;
  price: number | null;
  currency: string;
  requiresPayment: boolean;
  cancellableUntilHours: number;
  isActive: boolean;
  sortOrder: number;
  formFields: FormFieldDto[];
}

export interface DaySlotsDto {
  date: string; // ISO date (YYYY-MM-DD)
  slots: string[]; // ISO offset-date-times
}

export interface BookingRequestPayload {
  meetingTypeSlug: string;
  startAt: string;
  clientTimezone: string;
  clientName: string;
  clientEmail: string;
  clientPhone?: string;
  clientCompany?: string;
  formResponses?: Record<string, string>;
  inquiryId?: string;
  idempotencyKey?: string;
  source?: string;
  utmSource?: string;
  utmMedium?: string;
  utmCampaign?: string;
  landingPage?: string;
  referrer?: string;
}

export interface BookingDto {
  id: string;
  bookingNumber: string;
  meetingTypeName: string;
  meetingTypeSlug: string;
  locationType: LocationType;
  meetingUrl: string | null;
  startAt: string;
  endAt: string;
  clientTimezone: string;
  status: BookingStatus;
  clientName: string;
  clientEmail: string;
  price: number | null;
  currency: string | null;
  paymentStatus: BookingPaymentStatus;
  viewToken: string;
  rescheduleToken: string;
  cancelToken: string;
  cancellable: boolean;
  reschedulable: boolean;
}

export interface AdminBookingDto {
  id: string;
  bookingNumber: string;
  meetingTypeId: string;
  meetingTypeName: string;
  inquiryId: string | null;
  leadTier: 'HOT' | 'WARM' | 'NURTURE' | null;
  leadScore: number | null;
  clientName: string;
  clientEmail: string;
  clientPhone: string | null;
  clientCompany: string | null;
  clientTimezone: string;
  startAt: string;
  endAt: string;
  status: BookingStatus;
  outcome: BookingOutcome | null;
  internalNotes: string | null;
  cancelReason: string | null;
  noShow: boolean;
  price: number | null;
  currency: string | null;
  paymentStatus: BookingPaymentStatus;
  source: string | null;
  utmSource: string | null;
  utmMedium: string | null;
  utmCampaign: string | null;
  meetingUrl: string | null;
  formResponses: { fieldKey: string; label: string; value: string }[];
  confirmedAt: string | null;
  cancelledAt: string | null;
  completedAt: string | null;
  createdAt: string;
}

export interface BookingDashboardStatsDto {
  todayCount: number;
  upcomingCount: number;
  thisMonthCount: number;
  cancelledThisMonthCount: number;
  noShowThisMonthCount: number;
  hotLeadBookingsThisMonthCount: number;
  revenueThisMonth: number;
}

export interface BookingFunnelStatsDto {
  inquiries: number;
  qualifiedInquiries: number;
  booked: number;
  attended: number;
  proposalsSent: number;
  clientsWon: number;
  bookingRate: number | null;
  attendanceRate: number | null;
  proposalRate: number | null;
  closeRate: number | null;
}

export interface RevenueBySourceDto {
  source: string;
  bookingCount: number;
  revenue: number;
}

export interface AvailabilityWindowDto {
  id: string;
  dayOfWeek: number; // 1=Monday..7=Sunday
  startTime: string; // HH:mm:ss
  endTime: string;
  timezone: string;
  isActive: boolean;
}

export interface AvailabilityOverrideDto {
  id: string;
  overrideDate: string;
  isAvailable: boolean;
  startTime: string | null;
  endTime: string | null;
  reason: string | null;
}
