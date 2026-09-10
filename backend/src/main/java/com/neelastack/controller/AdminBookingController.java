package com.neelastack.controller;

import com.neelastack.dto.booking.AdminBookingDto;
import com.neelastack.dto.booking.AvailabilityOverrideDto;
import com.neelastack.dto.booking.AvailabilityOverrideRequest;
import com.neelastack.dto.booking.AvailabilityWindowDto;
import com.neelastack.dto.booking.AvailabilityWindowRequest;
import com.neelastack.dto.booking.BookingDashboardStatsDto;
import com.neelastack.dto.booking.BookingFunnelStatsDto;
import com.neelastack.dto.booking.BookingOutcomeRequest;
import com.neelastack.dto.booking.BookingStatusUpdateRequest;
import com.neelastack.dto.booking.MeetingTypeDto;
import com.neelastack.dto.booking.MeetingTypeRequest;
import com.neelastack.dto.booking.RevenueBySourceDto;
import com.neelastack.entity.BookingStatus;
import com.neelastack.service.AvailabilityService;
import com.neelastack.service.BookingAnalyticsService;
import com.neelastack.service.BookingService;
import com.neelastack.service.GoogleCalendarService;
import com.neelastack.service.MeetingTypeService;
import com.neelastack.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin management of the booking engine: meeting types, weekly availability & date
 * overrides, the bookings table + detail + status/outcome changes, dashboard/funnel/
 * revenue analytics, and the optional Google Calendar connect flow. Everything here
 * is already behind ROLE_ADMIN via SecurityConfig's "/api/v1/admin/**" rule — no
 * per-endpoint annotation needed — except the calendar OAuth callback, which Google
 * calls as a plain browser redirect (see SecurityConfig + GoogleCalendarService).
 */
@RestController
@RequestMapping("/api/v1/admin/booking")
@RequiredArgsConstructor
@Tag(name = "Admin — booking engine", description = "Requires ROLE_ADMIN (except the calendar OAuth callback)")
public class AdminBookingController {

    private final MeetingTypeService meetingTypeService;
    private final AvailabilityService availabilityService;
    private final BookingService bookingService;
    private final BookingAnalyticsService bookingAnalyticsService;
    private final GoogleCalendarService googleCalendarService;

    // --- Meeting types -----------------------------------------------------------------

    @GetMapping("/meeting-types")
    public List<MeetingTypeDto> listMeetingTypes() {
        return meetingTypeService.listAllForAdmin();
    }

    @PostMapping("/meeting-types")
    public ResponseEntity<MeetingTypeDto> createMeetingType(@Valid @RequestBody MeetingTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(meetingTypeService.create(request));
    }

    @PutMapping("/meeting-types/{id}")
    public MeetingTypeDto updateMeetingType(@PathVariable UUID id, @Valid @RequestBody MeetingTypeRequest request) {
        return meetingTypeService.update(id, request);
    }

    @DeleteMapping("/meeting-types/{id}")
    public ResponseEntity<Void> deleteMeetingType(@PathVariable UUID id) {
        meetingTypeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- Availability --------------------------------------------------------------------

    @GetMapping("/availability/windows")
    public List<AvailabilityWindowDto> listWindows() {
        return availabilityService.listWindows();
    }

    @PostMapping("/availability/windows")
    public ResponseEntity<AvailabilityWindowDto> addWindow(@Valid @RequestBody AvailabilityWindowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(availabilityService.addWindow(request));
    }

    @DeleteMapping("/availability/windows/{id}")
    public ResponseEntity<Void> removeWindow(@PathVariable UUID id) {
        availabilityService.removeWindow(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/availability/overrides")
    public List<AvailabilityOverrideDto> listOverrides(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return availabilityService.listOverrides(from, to);
    }

    @PostMapping("/availability/overrides")
    public AvailabilityOverrideDto upsertOverride(@Valid @RequestBody AvailabilityOverrideRequest request) {
        return availabilityService.upsertOverride(request);
    }

    @DeleteMapping("/availability/overrides/{date}")
    public ResponseEntity<Void> removeOverride(@PathVariable LocalDate date) {
        availabilityService.removeOverride(date);
        return ResponseEntity.noContent().build();
    }

    // --- Bookings --------------------------------------------------------------------------

    @GetMapping("/bookings")
    public Page<AdminBookingDto> listBookings(@RequestParam(required = false) BookingStatus status,
                                               @RequestParam(required = false) UUID meetingTypeId,
                                               @RequestParam(required = false) String search,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return bookingService.search(status, meetingTypeId, search,
                PaginationUtils.safePageable(page, size, Sort.by(Sort.Direction.DESC, "startAt")));
    }

    @GetMapping("/bookings/{id}")
    public AdminBookingDto getBooking(@PathVariable UUID id) {
        return bookingService.getAdminDetail(id);
    }

    @PatchMapping("/bookings/{id}/status")
    public AdminBookingDto updateStatus(@PathVariable UUID id, @Valid @RequestBody BookingStatusUpdateRequest request) {
        return bookingService.updateStatus(id, request.status());
    }

    @PatchMapping("/bookings/{id}/outcome")
    public AdminBookingDto recordOutcome(@PathVariable UUID id, @Valid @RequestBody BookingOutcomeRequest request) {
        return bookingService.recordOutcome(id, request);
    }

    // --- Dashboard / analytics --------------------------------------------------------------

    @GetMapping("/dashboard")
    public BookingDashboardStatsDto dashboard() {
        return bookingService.dashboardStats();
    }

    @GetMapping("/analytics/funnel")
    public BookingFunnelStatsDto funnel(@RequestParam(required = false) LocalDate from,
                                         @RequestParam(required = false) LocalDate to) {
        LocalDateTime fromDt = (from == null ? LocalDate.now().minusDays(30) : from).atStartOfDay();
        LocalDateTime toDt = (to == null ? LocalDate.now().plusDays(1) : to.plusDays(1)).atStartOfDay();
        return bookingAnalyticsService.funnel(fromDt, toDt);
    }

    @GetMapping("/analytics/revenue-by-source")
    public List<RevenueBySourceDto> revenueBySource(@RequestParam(required = false) LocalDate from,
                                                      @RequestParam(required = false) LocalDate to) {
        LocalDateTime fromDt = (from == null ? LocalDate.now().minusDays(30) : from).atStartOfDay();
        LocalDateTime toDt = (to == null ? LocalDate.now().plusDays(1) : to.plusDays(1)).atStartOfDay();
        return bookingAnalyticsService.revenueBySource(fromDt, toDt);
    }

    // --- Optional Google Calendar sync ------------------------------------------------------

    @GetMapping("/calendar/status")
    public Map<String, Object> calendarStatus() {
        return Map.of(
                "featureEnabled", googleCalendarService.isEnabled(),
                "connected", googleCalendarService.isConnected());
    }

    /** Authenticated (ROLE_ADMIN) — returns the Google consent URL for the admin's browser
     *  to navigate to. The URL embeds a short-lived one-time state token (see
     *  GoogleCalendarService); the callback itself is intentionally public. */
    @PostMapping("/calendar/connect")
    public Map<String, String> connect() {
        return Map.of("authorizationUrl", googleCalendarService.buildAuthorizationUrl());
    }

    @PostMapping("/calendar/disconnect")
    public ResponseEntity<Void> disconnect() {
        googleCalendarService.disconnect();
        return ResponseEntity.noContent().build();
    }

    /** Public route (see SecurityConfig) — Google redirects the admin's browser here directly
     *  after consent. Redirects on to the admin dashboard's calendar-settings page either way,
     *  with a query flag indicating success/failure, so this never has to render its own page. */
    @GetMapping("/calendar/callback")
    public void calendarCallback(@RequestParam String code, @RequestParam String state,
                                  HttpServletResponse response) throws IOException {
        try {
            googleCalendarService.verifyState(state);
            googleCalendarService.handleOAuthCallback(code);
            response.sendRedirect("/admin/settings/booking?calendar=connected");
        } catch (Exception e) {
            response.sendRedirect("/admin/settings/booking?calendar=error");
        }
    }
}
