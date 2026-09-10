package com.neelastack.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neelastack.dto.booking.AdminBookingDto;
import com.neelastack.dto.booking.BookingDashboardStatsDto;
import com.neelastack.dto.booking.BookingDto;
import com.neelastack.dto.booking.BookingOutcomeRequest;
import com.neelastack.dto.booking.BookingRequest;
import com.neelastack.dto.booking.RescheduleRequest;
import com.neelastack.entity.AuditAction;
import com.neelastack.entity.Booking;
import com.neelastack.entity.BookingFormResponse;
import com.neelastack.entity.BookingPaymentStatus;
import com.neelastack.entity.BookingStatus;
import com.neelastack.entity.Inquiry;
import com.neelastack.entity.MeetingType;
import com.neelastack.exception.BadRequestException;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.BookingFormFieldRepository;
import com.neelastack.repository.BookingFormResponseRepository;
import com.neelastack.repository.BookingRepository;
import com.neelastack.repository.InquiryRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The booking lifecycle: create (with server-side slot re-validation +
 * idempotency),
 * client self-service reschedule/cancel via secure tokens, admin status/outcome
 * changes, listing, and dashboard stats. See master prompt sections 15-21,
 * 27-34.
 *
 * Reschedule is implemented as an in-place time update on the same row (not a
 * new
 * booking row linked via rescheduled_from_id) -- simpler, and the database
 * exclusion
 * constraint still protects it exactly the same way a fresh booking would be
 * protected. The audit log (BOOKING_RESCHEDULED, with before/after times in its
 * metadata) is the history trail for "this was rescheduled," rather than a
 * second
 * database row.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingFormResponseRepository formResponseRepository;
    private final BookingFormFieldRepository formFieldRepository;
    private final InquiryRepository inquiryRepository;
    private final MeetingTypeService meetingTypeService;
    private final AvailabilityService availabilityService;
    private final GoogleCalendarService googleCalendarService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    private static final SecureRandom RANDOM = new SecureRandom();

    // ------------------------------------------------------------------ create
    // --------

    @Transactional
    public BookingDto create(BookingRequest request) {
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<Booking> existing = bookingRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                return toPublicDto(existing.get());
            }
        }

        MeetingType meetingType = meetingTypeService.getBySlugOrThrow(request.meetingTypeSlug());
        if (!Boolean.TRUE.equals(meetingType.getIsActive())) {
            throw new BadRequestException("This meeting type is not currently open for booking");
        }

        validateClientTimezone(request.clientTimezone());
        validateFormResponses(meetingType.getId(), request.formResponses());
        availabilityService.validateSlotOrThrow(meetingType, request.startAt());

        Inquiry inquiry = null;
        if (request.inquiryId() != null) {
            inquiry = inquiryRepository.findById(request.inquiryId()).orElse(null);
            if (inquiry == null) {
                throw new BadRequestException("The linked inquiry no longer exists.");
            }
            if (!inquiry.getEmail().equalsIgnoreCase(request.clientEmail().trim())) {
                throw new BadRequestException("The booking details do not match the linked inquiry.");
            }
        }

        OffsetDateTime startAt = request.startAt();
        OffsetDateTime endAt = startAt.plusMinutes(meetingType.getDurationMinutes());

        Booking booking = Booking.builder()
                .bookingNumber(nextBookingNumber())
                .meetingTypeId(meetingType.getId())
                .inquiryId(request.inquiryId())
                .clientName(request.clientName().trim())
                .clientEmail(request.clientEmail().trim().toLowerCase())
                .clientPhone(request.clientPhone())
                .clientCompany(request.clientCompany())
                .clientTimezone(request.clientTimezone() == null || request.clientTimezone().isBlank()
                        ? "Asia/Kolkata"
                        : request.clientTimezone())
                .startAt(startAt)
                .endAt(endAt)
                .status(BookingStatus.CONFIRMED)
                .viewToken(generateToken())
                .rescheduleToken(generateToken())
                .cancelToken(generateToken())
                .idempotencyKey(request.idempotencyKey())
                .requiresPayment(Boolean.TRUE.equals(meetingType.getRequiresPayment()))
                .price(meetingType.getPrice())
                .currency(meetingType.getCurrency())
                .paymentStatus(Boolean.TRUE.equals(meetingType.getRequiresPayment())
                        ? BookingPaymentStatus.PENDING
                        : BookingPaymentStatus.NOT_REQUIRED)
                .source(inquiry != null ? "inquiry" : request.source())
                .utmSource(inquiry != null ? inquiry.getUtmSource() : request.utmSource())
                .utmMedium(inquiry != null ? inquiry.getUtmMedium() : request.utmMedium())
                .utmCampaign(inquiry != null ? inquiry.getUtmCampaign() : request.utmCampaign())
                .landingPage(inquiry != null ? inquiry.getLandingPage() : request.landingPage())
                .referrer(inquiry != null ? inquiry.getReferrer() : request.referrer())
                .meetingUrl(meetingType.getLocationDetail())
                .confirmedAt(LocalDateTime.now())
                .build();

        try {
            booking = bookingRepository.save(booking);
        } catch (DataIntegrityViolationException e) {
            throw new BadRequestException("That time was just booked by someone else. Please pick another slot.");
        }

        saveFormResponses(booking.getId(), meetingType.getId(), request.formResponses());

        if (googleCalendarService.isEnabled()) {
            final Booking bookingForCalendar = booking;
            googleCalendarService.createEventSafely(bookingForCalendar, meetingType.getName()).ifPresent(result -> {
                bookingForCalendar.setCalendarEventId(result[0]);
                if (result[1] != null) {
                    bookingForCalendar.setMeetingUrl(result[1]);
                }
                bookingRepository.save(bookingForCalendar);
            });
        }

        emailService.sendBookingConfirmation(booking, meetingType);
        emailService.sendAdminBookingAlert(booking, meetingType, inquiry);
        booking.setConfirmationSentAt(LocalDateTime.now());
        booking = bookingRepository.save(booking);

        auditLogService.recordBestEffort(AuditAction.BOOKING_CREATED, "Booking", booking.getId().toString(),
                Map.of("meetingType", meetingType.getSlug(), "startAt", booking.getStartAt().toString()));

        return toPublicDto(booking);
    }

    // --------------------------------------------------------------- client
    // self-service

    @Transactional(readOnly = true)
    public BookingDto getByViewToken(String token) {
        Booking booking = bookingRepository.findByViewToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        return toPublicDto(booking);
    }

    @Transactional
    public BookingDto reschedule(String rescheduleToken, RescheduleRequest request) {
        Booking booking = bookingRepository.findByRescheduleToken(rescheduleToken)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.COMPLETED) {
            throw new BadRequestException("This booking can no longer be rescheduled");
        }

        MeetingType meetingType = meetingTypeService.getByIdOrThrow(booking.getMeetingTypeId());
        availabilityService.validateSlotOrThrow(meetingType, request.newStartAt());

        OffsetDateTime oldStart = booking.getStartAt();
        booking.setStartAt(request.newStartAt());
        booking.setEndAt(request.newStartAt().plusMinutes(meetingType.getDurationMinutes()));
        if (request.clientTimezone() != null && !request.clientTimezone().isBlank()) {
            validateClientTimezone(request.clientTimezone());
            booking.setClientTimezone(request.clientTimezone());
        }

        if (booking.getCalendarEventId() != null) {
            googleCalendarService.deleteEventSafely(booking.getCalendarEventId());
            booking.setCalendarEventId(null);
            if (googleCalendarService.isEnabled()) {
                final Booking bookingForCalendar = booking;
                googleCalendarService.createEventSafely(bookingForCalendar, meetingType.getName())
                        .ifPresent(result -> {
                            bookingForCalendar.setCalendarEventId(result[0]);
                            if (result[1] != null)
                                bookingForCalendar.setMeetingUrl(result[1]);
                        });
            }
        }

        try {
            booking = bookingRepository.save(booking);
        } catch (DataIntegrityViolationException e) {
            throw new BadRequestException("That time was just booked by someone else. Please pick another slot.");
        }

        emailService.sendRescheduleConfirmation(booking, meetingType, oldStart);

        auditLogService.recordBestEffort(AuditAction.BOOKING_RESCHEDULED, "Booking", booking.getId().toString(),
                Map.of("oldStartAt", oldStart.toString(), "newStartAt", booking.getStartAt().toString()));

        return toPublicDto(booking);
    }

    @Transactional
    public BookingDto cancel(String cancelToken, String reason) {
        Booking booking = bookingRepository.findByCancelToken(cancelToken)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return toPublicDto(booking);
        }

        MeetingType meetingType = meetingTypeService.getByIdOrThrow(booking.getMeetingTypeId());
        long hoursUntilStart = ChronoUnit.HOURS.between(OffsetDateTime.now(ZoneOffset.UTC), booking.getStartAt());
        if (hoursUntilStart < meetingType.getCancellableUntilHours()) {
            throw new BadRequestException("This booking can no longer be cancelled (inside the "
                    + meetingType.getCancellableUntilHours()
                    + "-hour cancellation window). Please contact us directly.");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelReason(reason);
        booking.setCancelledAt(LocalDateTime.now());
        booking = bookingRepository.save(booking);

        if (booking.getCalendarEventId() != null) {
            googleCalendarService.deleteEventSafely(booking.getCalendarEventId());
        }

        emailService.sendCancellationConfirmation(booking, meetingType);

        auditLogService.recordBestEffort(AuditAction.BOOKING_CANCELLED, "Booking", booking.getId().toString(),
                Map.of("reason", reason == null ? "" : reason));

        return toPublicDto(booking);
    }

    // ------------------------------------------------------------------------
    // admin ----

    @Transactional(readOnly = true)
    public Page<AdminBookingDto> search(BookingStatus status, UUID meetingTypeId, String searchText,
            Pageable pageable) {
        String likeTerm = (searchText == null || searchText.isBlank()) ? null : "%" + searchText.toLowerCase() + "%";
        return bookingRepository.search(status, meetingTypeId, likeTerm, pageable).map(this::toAdminDto);
    }

    @Transactional(readOnly = true)
    public AdminBookingDto getAdminDetail(UUID id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));
        return toAdminDto(booking);
    }

    @Transactional
    public AdminBookingDto updateStatus(UUID id, BookingStatus newStatus) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));
        BookingStatus previous = booking.getStatus();
        booking.setStatus(newStatus);

        if (newStatus == BookingStatus.COMPLETED) {
            booking.setCompletedAt(LocalDateTime.now());
        } else if (newStatus == BookingStatus.NO_SHOW) {
            booking.setNoShow(true);
            booking.setCompletedAt(LocalDateTime.now());
            MeetingType meetingType = meetingTypeService.getByIdOrThrow(booking.getMeetingTypeId());
            emailService.sendNoShowFollowUp(booking, meetingType);
        }

        booking = bookingRepository.save(booking);
        auditLogService.recordBestEffort(AuditAction.BOOKING_STATUS_CHANGED, "Booking", booking.getId().toString(),
                Map.of("from", previous.name(), "to", newStatus.name()));
        return toAdminDto(booking);
    }

    @Transactional
    public AdminBookingDto recordOutcome(UUID id, BookingOutcomeRequest request) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));
        booking.setOutcome(request.outcome());
        if (request.internalNotes() != null) {
            booking.setInternalNotes(request.internalNotes());
        }
        booking = bookingRepository.save(booking);
        auditLogService.recordBestEffort(AuditAction.BOOKING_OUTCOME_RECORDED, "Booking", booking.getId().toString(),
                Map.of("outcome", request.outcome().name()));
        return toAdminDto(booking);
    }

    @Transactional(readOnly = true)
    public BookingDashboardStatsDto dashboardStats() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime startOfToday = now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime startOfTomorrow = startOfToday.plusDays(1);
        OffsetDateTime startOfMonth = now.toLocalDate().withDayOfMonth(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime startOfNextMonth = startOfMonth.plusMonths(1);

        long today = bookingRepository.countLiveBetween(startOfToday, startOfTomorrow);
        long upcoming = bookingRepository.countLiveBetween(now, startOfMonth.plusMonths(2));
        long thisMonth = bookingRepository.countLiveBetween(startOfMonth, startOfNextMonth);
        long cancelledThisMonth = bookingRepository.countByStatus(BookingStatus.CANCELLED);
        long noShowThisMonth = bookingRepository.countByNoShowTrue();
        BigDecimal revenue = bookingRepository.sumRevenueBetween(startOfMonth, startOfNextMonth);

        return BookingDashboardStatsDto.builder()
                .todayCount(today)
                .upcomingCount(upcoming)
                .thisMonthCount(thisMonth)
                .cancelledThisMonthCount(cancelledThisMonth)
                .noShowThisMonthCount(noShowThisMonth)
                .hotLeadBookingsThisMonthCount(countHotLeadBookings(startOfMonth, startOfNextMonth))
                .revenueThisMonth(revenue == null ? BigDecimal.ZERO : revenue)
                .build();
    }

    private long countHotLeadBookings(OffsetDateTime from, OffsetDateTime to) {
        return bookingRepository.findLiveBetween(from, to).stream()
                .filter(b -> b.getInquiryId() != null)
                .map(b -> inquiryRepository.findById(b.getInquiryId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(i -> i.getLeadTier() != null && i.getLeadTier().name().equals("HOT"))
                .count();
    }

    // ---------------------------------------------------------------- helpers
    // ----------

    private void validateFormResponses(UUID meetingTypeId, Map<String, String> responses) {
        List<com.neelastack.entity.BookingFormField> fields = formFieldRepository
                .findByMeetingTypeIdOrderBySortOrderAsc(meetingTypeId);
        Map<String, com.neelastack.entity.BookingFormField> configured = fields.stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.neelastack.entity.BookingFormField::getFieldKey,
                        f -> f,
                        (first, ignored) -> first));

        if (responses != null) {
            for (String key : responses.keySet()) {
                if (!configured.containsKey(key)) {
                    throw new BadRequestException("Unknown booking form field: " + key);
                }
            }
        }

        for (com.neelastack.entity.BookingFormField field : fields) {
            String value = responses == null ? null : responses.get(field.getFieldKey());
            if (Boolean.TRUE.equals(field.getIsRequired()) && (value == null || value.isBlank())) {
                throw new BadRequestException("Please complete the required field: " + field.getLabel());
            }

            if (value != null && value.length() > 4000) {
                throw new BadRequestException("Booking form response is too long for field: " + field.getLabel());
            }

            if (value != null && field.getOptions() != null &&
                    (field.getFieldType() == com.neelastack.entity.FormFieldType.SELECT
                            || field.getFieldType() == com.neelastack.entity.FormFieldType.RADIO)) {
                try {
                    List<String> options = objectMapper.readValue(field.getOptions(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                    if (!options.contains(value)) {
                        throw new BadRequestException("Invalid option selected for field: " + field.getLabel());
                    }
                } catch (BadRequestException e) {
                    throw e;
                } catch (Exception e) {
                    log.warn("Could not validate configured options for booking field {}: {}",
                            field.getFieldKey(), e.getMessage());
                }
            }
        }
    }

    private void saveFormResponses(UUID bookingId, UUID meetingTypeId, Map<String, String> responses) {
        if (responses == null || responses.isEmpty()) {
            return;
        }
        Map<String, com.neelastack.entity.BookingFormField> fields = formFieldRepository
                .findByMeetingTypeIdOrderBySortOrderAsc(meetingTypeId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.neelastack.entity.BookingFormField::getFieldKey,
                        f -> f));
        responses.forEach((key, value) -> {
            com.neelastack.entity.BookingFormField field = fields.get(key);
            formResponseRepository.save(BookingFormResponse.builder()
                    .bookingId(bookingId)
                    .fieldKey(key)
                    .label(field != null ? field.getLabel() : key)
                    .value(value)
                    .build());
        });
    }

    private void validateClientTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (Exception e) {
            throw new BadRequestException("Invalid client timezone");
        }
    }

    private String nextBookingNumber() {
        Number seq = (Number) entityManager.createNativeQuery("SELECT nextval('booking_number_seq')").getSingleResult();
        int year = java.time.Year.now().getValue();
        return "NS-" + year + "-" + String.format("%06d", seq.longValue());
    }

    private String generateToken() {
        byte[] bytes = new byte[36];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private BookingDto toPublicDto(Booking b) {
        MeetingType meetingType = meetingTypeService.getByIdOrThrow(b.getMeetingTypeId());
        long hoursUntilStart = ChronoUnit.HOURS.between(OffsetDateTime.now(ZoneOffset.UTC), b.getStartAt());
        boolean cancellable = b.getStatus() != BookingStatus.CANCELLED && b.getStatus() != BookingStatus.COMPLETED
                && hoursUntilStart >= meetingType.getCancellableUntilHours();
        boolean reschedulable = b.getStatus() != BookingStatus.CANCELLED && b.getStatus() != BookingStatus.COMPLETED;

        return BookingDto.builder()
                .id(b.getId()).bookingNumber(b.getBookingNumber()).meetingTypeName(meetingType.getName())
                .meetingTypeSlug(meetingType.getSlug())
                .locationType(meetingType.getLocationType()).meetingUrl(b.getMeetingUrl())
                .startAt(b.getStartAt()).endAt(b.getEndAt()).clientTimezone(b.getClientTimezone())
                .status(b.getStatus()).clientName(b.getClientName()).clientEmail(b.getClientEmail())
                .price(b.getPrice()).currency(b.getCurrency()).paymentStatus(b.getPaymentStatus())
                .viewToken(b.getViewToken()).rescheduleToken(b.getRescheduleToken()).cancelToken(b.getCancelToken())
                .cancellable(cancellable).reschedulable(reschedulable)
                .build();
    }

    private AdminBookingDto toAdminDto(Booking b) {
        MeetingType meetingType = meetingTypeService.getByIdOrThrow(b.getMeetingTypeId());
        Inquiry inquiry = b.getInquiryId() != null ? inquiryRepository.findById(b.getInquiryId()).orElse(null) : null;
        List<Map<String, String>> responses = formResponseRepository.findByBookingId(b.getId()).stream()
                .map(r -> Map.of("fieldKey", r.getFieldKey(), "label", r.getLabel(), "value",
                        r.getValue() == null ? "" : r.getValue()))
                .toList();

        return AdminBookingDto.builder()
                .id(b.getId()).bookingNumber(b.getBookingNumber()).meetingTypeId(meetingType.getId())
                .meetingTypeName(meetingType.getName())
                .inquiryId(b.getInquiryId())
                .leadTier(inquiry != null ? inquiry.getLeadTier() : null)
                .leadScore(inquiry != null ? inquiry.getLeadScore() : null)
                .clientName(b.getClientName()).clientEmail(b.getClientEmail()).clientPhone(b.getClientPhone())
                .clientCompany(b.getClientCompany()).clientTimezone(b.getClientTimezone())
                .startAt(b.getStartAt()).endAt(b.getEndAt()).status(b.getStatus()).outcome(b.getOutcome())
                .internalNotes(b.getInternalNotes()).cancelReason(b.getCancelReason()).noShow(b.getNoShow())
                .price(b.getPrice()).currency(b.getCurrency()).paymentStatus(b.getPaymentStatus())
                .source(b.getSource()).utmSource(b.getUtmSource()).utmMedium(b.getUtmMedium())
                .utmCampaign(b.getUtmCampaign()).meetingUrl(b.getMeetingUrl()).formResponses(responses)
                .confirmedAt(b.getConfirmedAt()).cancelledAt(b.getCancelledAt()).completedAt(b.getCompletedAt())
                .createdAt(b.getCreatedAt())
                .build();
    }
}
