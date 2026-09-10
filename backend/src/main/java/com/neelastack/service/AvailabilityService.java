package com.neelastack.service;

import com.neelastack.dto.booking.AvailabilityOverrideDto;
import com.neelastack.dto.booking.AvailabilityOverrideRequest;
import com.neelastack.dto.booking.AvailabilityWindowDto;
import com.neelastack.dto.booking.AvailabilityWindowRequest;
import com.neelastack.dto.booking.DaySlotsDto;
import com.neelastack.entity.AvailabilityDateOverride;
import com.neelastack.entity.AvailabilityWindow;
import com.neelastack.entity.Booking;
import com.neelastack.entity.MeetingType;
import com.neelastack.exception.BadRequestException;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.AvailabilityDateOverrideRepository;
import com.neelastack.repository.AvailabilityWindowRepository;
import com.neelastack.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The availability engine. Computes real, bookable slot start-times for a
 * meeting
 * type over a date range, honouring: recurring weekly windows (with gaps
 * between
 * windows acting as breaks), one-off date overrides (holidays / special hours),
 * per-meeting-type buffers, minimum booking notice, maximum booking horizon,
 * and
 * conflicts with existing live bookings.
 *
 * Timestamps are computed and compared in UTC internally; the caller-facing DTO
 * carries the correct zone offset for the client's requested timezone so the
 * frontend never has to do its own DST-aware conversion.
 *
 * Not covered here (documented limitation, not silently approximated as more
 * than
 * it is): a candidate slot's buffer is checked against the *raw* start/end of
 * other
 * bookings, not their own buffers — the true, final word on whether two
 * bookings can
 * coexist is the database-level exclusion constraint (see V33 migration), which
 * this
 * pre-check exists only to keep the public availability list honest and avoid
 * offering slots that would just bounce off that constraint.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AvailabilityService {

    private final AvailabilityWindowRepository availabilityWindowRepository;
    private final AvailabilityDateOverrideRepository availabilityDateOverrideRepository;
    private final BookingRepository bookingRepository;
    private final GoogleCalendarService googleCalendarService;

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    /**
     * Computes bookable slots for every date in [today, today + maxHorizonDays]
     * (capped
     * at the caller's requested range), grouped by calendar date in the client's
     * timezone.
     */
    @Transactional(readOnly = true)
    public List<DaySlotsDto> computeAvailability(MeetingType meetingType, LocalDate fromDate, LocalDate toDate,
            String clientTimezoneRaw) {
        ZoneId clientZone = parseZone(clientTimezoneRaw);

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate effectiveFrom = fromDate.isBefore(today) ? today : fromDate;
        LocalDate horizonEnd = today.plusDays(meetingType.getMaxHorizonDays());
        LocalDate effectiveTo = toDate.isAfter(horizonEnd) ? horizonEnd : toDate;
        if (effectiveTo.isBefore(effectiveFrom)) {
            return List.of();
        }

        List<AvailabilityWindow> windows = availabilityWindowRepository.findByIsActiveTrue();
        List<AvailabilityDateOverride> overrides = availabilityDateOverrideRepository
                .findByOverrideDateBetweenOrderByOverrideDateAsc(effectiveFrom, effectiveTo);

        Instant now = Instant.now();
        Instant earliestBookable = now.plus(Duration.ofMinutes(meetingType.getMinNoticeMinutes()));

        OffsetDateTime rangeStart = effectiveFrom.atStartOfDay(BUSINESS_ZONE).minusDays(1).toOffsetDateTime();
        OffsetDateTime rangeEnd = effectiveTo.plusDays(2).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        List<Booking> existingBookings = bookingRepository.findLiveBetween(rangeStart, rangeEnd);

        List<GoogleCalendarService.Interval> busyFromCalendar = googleCalendarService.isEnabled()
                ? googleCalendarService.listBusyIntervalsSafely(rangeStart.toInstant(), rangeEnd.toInstant())
                : List.of();

        List<DaySlotsDto> result = new ArrayList<>();
        long durationMinutes = meetingType.getDurationMinutes();
        long bufferBefore = meetingType.getBufferBeforeMinutes();
        long bufferAfter = meetingType.getBufferAfterMinutes();

        long dayCount = java.time.temporal.ChronoUnit.DAYS.between(effectiveFrom, effectiveTo);
        for (long dayOffset = 0; dayOffset <= dayCount; dayOffset++) {
            final LocalDate currentDate = effectiveFrom.plusDays(dayOffset);
            List<Instant> daySlots = new ArrayList<>();

            Optional<AvailabilityDateOverride> override = overrides.stream()
                    .filter(o -> o.getOverrideDate().equals(currentDate))
                    .findFirst();

            List<LocalTime[]> dayWindows = new ArrayList<>();
            if (override.isPresent()) {
                AvailabilityDateOverride o = override.get();
                if (Boolean.TRUE.equals(o.getIsAvailable()) && o.getStartTime() != null) {
                    dayWindows.add(new LocalTime[] { o.getStartTime(), o.getEndTime() });
                }
                // isAvailable=false (or no times set) => whole day blocked, dayWindows stays
                // empty.
            } else {
                int isoDayOfWeek = currentDate.getDayOfWeek().getValue(); // 1=Monday..7=Sunday
                windows.stream()
                        .filter(w -> w.getDayOfWeek().equals(isoDayOfWeek))
                        .sorted(Comparator.comparing(AvailabilityWindow::getStartTime))
                        .forEach(w -> dayWindows.add(new LocalTime[] { w.getStartTime(), w.getEndTime() }));
            }

            for (LocalTime[] window : dayWindows) {
                ZonedDateTime windowStart = currentDate.atTime(window[0]).atZone(BUSINESS_ZONE);
                ZonedDateTime windowEnd = currentDate.atTime(window[1]).atZone(BUSINESS_ZONE);

                ZonedDateTime candidate = windowStart;
                while (!candidate.plusMinutes(durationMinutes).isAfter(windowEnd)) {
                    Instant slotStart = candidate.toInstant();
                    Instant slotEnd = slotStart.plus(Duration.ofMinutes(durationMinutes));
                    Instant bufferedStart = slotStart.minus(Duration.ofMinutes(bufferBefore));
                    Instant bufferedEnd = slotEnd.plus(Duration.ofMinutes(bufferAfter));

                    boolean tooSoon = slotStart.isBefore(earliestBookable);
                    boolean conflictsBooking = existingBookings.stream()
                            .anyMatch(b -> bufferedStart.isBefore(b.getEndAt().toInstant())
                                    && bufferedEnd.isAfter(b.getStartAt().toInstant()));
                    boolean conflictsCalendar = googleCalendarService.overlapsAnyBusyInterval(
                            busyFromCalendar, bufferedStart, bufferedEnd);

                    if (!tooSoon && !conflictsBooking && !conflictsCalendar) {
                        daySlots.add(slotStart);
                    }
                    candidate = candidate.plusMinutes(durationMinutes);
                }
            }

            if (!daySlots.isEmpty()) {
                java.util.Map<LocalDate, List<OffsetDateTime>> byClientDate = daySlots.stream()
                        .map(instant -> instant.atZone(clientZone).toOffsetDateTime())
                        .sorted()
                        .collect(java.util.stream.Collectors.groupingBy(
                                value -> value.toLocalDate(),
                                java.util.LinkedHashMap::new,
                                java.util.stream.Collectors.toList()));
                byClientDate.forEach((clientDate, converted) -> result
                        .add(DaySlotsDto.builder().date(clientDate).slots(converted).build()));
            }
        }

        result.sort(java.util.Comparator.comparing(DaySlotsDto::date));
        return result;
    }

    /**
     * Server-side re-validation of a single candidate slot at booking-creation time
     * --
     * the availability list above is advisory; this is the last line of defence
     * before
     * the DB exclusion constraint itself.
     */
    @Transactional(readOnly = true)
    public void validateSlotOrThrow(MeetingType meetingType, OffsetDateTime startAt) {
        Instant now = Instant.now();
        Instant earliestBookable = now.plus(Duration.ofMinutes(meetingType.getMinNoticeMinutes()));
        Instant latestBookable = now.plus(Duration.ofDays(meetingType.getMaxHorizonDays()));
        Instant slotStart = startAt.toInstant();

        if (slotStart.isBefore(earliestBookable)) {
            throw new BadRequestException(
                    "This time no longer meets the minimum booking notice. Please pick another slot.");
        }
        if (slotStart.isAfter(latestBookable)) {
            throw new BadRequestException("This time is too far in the future to book yet.");
        }

        Instant slotEnd = slotStart.plus(Duration.ofMinutes(meetingType.getDurationMinutes()));
        OffsetDateTime start = startAt;
        OffsetDateTime end = start.plusMinutes(meetingType.getDurationMinutes());

        LocalDate date = startAt.atZoneSameInstant(BUSINESS_ZONE).toLocalDate();
        boolean withinOpenWindow = isWithinAvailability(date, startAt.atZoneSameInstant(BUSINESS_ZONE).toLocalTime(),
                startAt.atZoneSameInstant(BUSINESS_ZONE).toLocalTime().plusMinutes(meetingType.getDurationMinutes()));
        if (!withinOpenWindow) {
            throw new BadRequestException("That time falls outside our available hours.");
        }

        List<Booking> overlapping = bookingRepository.findOverlapping(
                start.minusMinutes(meetingType.getBufferBeforeMinutes()),
                end.plusMinutes(meetingType.getBufferAfterMinutes()));
        if (!overlapping.isEmpty()) {
            throw new BadRequestException("That time was just booked by someone else. Please pick another slot.");
        }
    }

    private boolean isWithinAvailability(LocalDate date, LocalTime start, LocalTime end) {
        Optional<AvailabilityDateOverride> override = availabilityDateOverrideRepository.findByOverrideDate(date);
        if (override.isPresent()) {
            AvailabilityDateOverride o = override.get();
            if (!Boolean.TRUE.equals(o.getIsAvailable()) || o.getStartTime() == null) {
                return false;
            }
            return !start.isBefore(o.getStartTime()) && !end.isAfter(o.getEndTime());
        }
        int isoDayOfWeek = date.getDayOfWeek().getValue();
        return availabilityWindowRepository.findByDayOfWeekAndIsActiveTrue(isoDayOfWeek).stream()
                .anyMatch(w -> !start.isBefore(w.getStartTime()) && !end.isAfter(w.getEndTime()));
    }

    private ZoneId parseZone(String raw) {
        if (raw == null || raw.isBlank()) {
            return BUSINESS_ZONE;
        }
        try {
            return ZoneId.of(raw);
        } catch (Exception e) {
            log.warn("Invalid timezone '{}' in availability request, falling back to {}", raw, BUSINESS_ZONE);
            return BUSINESS_ZONE;
        }
    }

    // --- Admin management of weekly windows & date overrides
    // -------------------------

    @Transactional
    public AvailabilityWindowDto addWindow(AvailabilityWindowRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BadRequestException("End time must be after start time");
        }
        AvailabilityWindow window = AvailabilityWindow.builder()
                .dayOfWeek(request.dayOfWeek())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .timezone(request.timezone() == null || request.timezone().isBlank() ? "Asia/Kolkata"
                        : request.timezone())
                .isActive(true)
                .build();
        window = availabilityWindowRepository.save(window);
        return toDto(window);
    }

    @Transactional
    public void removeWindow(UUID id) {
        if (!availabilityWindowRepository.existsById(id)) {
            throw new ResourceNotFoundException("Availability window not found: " + id);
        }
        availabilityWindowRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityWindowDto> listWindows() {
        return availabilityWindowRepository.findAllByOrderByDayOfWeekAscStartTimeAsc()
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public AvailabilityOverrideDto upsertOverride(AvailabilityOverrideRequest request) {
        if (request.isAvailable() && (request.startTime() == null || request.endTime() == null
                || !request.endTime().isAfter(request.startTime()))) {
            throw new BadRequestException("A special-hours override needs a valid start and end time");
        }
        availabilityDateOverrideRepository.deleteByOverrideDate(request.overrideDate());
        AvailabilityDateOverride override = AvailabilityDateOverride.builder()
                .overrideDate(request.overrideDate())
                .isAvailable(request.isAvailable())
                .startTime(request.isAvailable() ? request.startTime() : null)
                .endTime(request.isAvailable() ? request.endTime() : null)
                .reason(request.reason())
                .build();
        override = availabilityDateOverrideRepository.save(override);
        return toDto(override);
    }

    @Transactional
    public void removeOverride(LocalDate date) {
        availabilityDateOverrideRepository.deleteByOverrideDate(date);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityOverrideDto> listOverrides(LocalDate from, LocalDate to) {
        return availabilityDateOverrideRepository.findByOverrideDateBetweenOrderByOverrideDateAsc(from, to)
                .stream().map(this::toDto).toList();
    }

    private AvailabilityWindowDto toDto(AvailabilityWindow w) {
        return AvailabilityWindowDto.builder()
                .id(w.getId()).dayOfWeek(w.getDayOfWeek()).startTime(w.getStartTime())
                .endTime(w.getEndTime()).timezone(w.getTimezone()).isActive(w.getIsActive())
                .build();
    }

    private AvailabilityOverrideDto toDto(AvailabilityDateOverride o) {
        return AvailabilityOverrideDto.builder()
                .id(o.getId()).overrideDate(o.getOverrideDate()).isAvailable(o.getIsAvailable())
                .startTime(o.getStartTime()).endTime(o.getEndTime()).reason(o.getReason())
                .build();
    }
}
