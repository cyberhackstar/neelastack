package com.neelastack.controller;

import com.neelastack.dto.booking.BookingDto;
import com.neelastack.dto.booking.BookingRequest;
import com.neelastack.dto.booking.CancelRequest;
import com.neelastack.dto.booking.DaySlotsDto;
import com.neelastack.dto.booking.MeetingTypeDto;
import com.neelastack.dto.booking.RescheduleRequest;
import com.neelastack.entity.MeetingType;
import com.neelastack.service.AvailabilityService;
import com.neelastack.service.BookingService;
import com.neelastack.service.MeetingTypeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * The public, no-login booking flow: browse meeting types, see real availability,
 * book, and self-service view/reschedule/cancel via secure tokens (never a raw id).
 * See master prompt sections 6, 15, 17, 18, 45.
 */
@RestController
@RequestMapping("/api/v1/public/booking")
@RequiredArgsConstructor
@Tag(name = "Public — booking", description = "No login required")
public class PublicBookingController {

    private final MeetingTypeService meetingTypeService;
    private final AvailabilityService availabilityService;
    private final BookingService bookingService;

    @GetMapping("/meeting-types")
    public List<MeetingTypeDto> listMeetingTypes() {
        return meetingTypeService.listPublic();
    }

    @GetMapping("/meeting-types/{slug}")
    public MeetingTypeDto getMeetingType(@PathVariable String slug) {
        return meetingTypeService.getPublicBySlug(slug);
    }

    @GetMapping("/availability")
    public List<DaySlotsDto> availability(@RequestParam String slug,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(defaultValue = "Asia/Kolkata") String timezone) {
        MeetingType meetingType = meetingTypeService.getBySlugOrThrow(slug);
        return availabilityService.computeAvailability(meetingType, from, to, timezone);
    }

    @PostMapping("/bookings")
    public ResponseEntity<BookingDto> createBooking(@Valid @RequestBody BookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(request));
    }

    @GetMapping("/bookings/{viewToken}")
    public BookingDto getBooking(@PathVariable String viewToken) {
        return bookingService.getByViewToken(viewToken);
    }

    @PostMapping("/bookings/{rescheduleToken}/reschedule")
    public BookingDto reschedule(@PathVariable String rescheduleToken, @Valid @RequestBody RescheduleRequest request) {
        return bookingService.reschedule(rescheduleToken, request);
    }

    @PostMapping("/bookings/{cancelToken}/cancel")
    public BookingDto cancel(@PathVariable String cancelToken, @RequestBody(required = false) CancelRequest request) {
        return bookingService.cancel(cancelToken, request == null ? null : request.reason());
    }
}
