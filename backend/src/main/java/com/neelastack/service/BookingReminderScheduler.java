package com.neelastack.service;

import com.neelastack.entity.Booking;
import com.neelastack.entity.BookingPaymentStatus;
import com.neelastack.entity.MeetingType;
import com.neelastack.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Scheduled housekeeping for the booking engine: 24h/1h reminder emails (feature 16/38),
 * releasing expired never-paid payment holds (feature 21/41), and flagging past
 * meetings that were never marked completed/no-show/cancelled so they don't just sit
 * open forever. Mirrors the cadence/style of LeadFollowUpService's existing @Scheduled jobs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingReminderScheduler {

    private final BookingRepository bookingRepository;
    private final MeetingTypeService meetingTypeService;
    private final EmailService emailService;

    /** Every 15 minutes: send the 24h-ahead reminder to anyone whose meeting starts in the
     *  next 23-25 hour window and hasn't already received one. */
    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void send24hReminders() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        var due = bookingRepository.findDue24hReminders(now.plusHours(23), now.plusHours(25));
        for (Booking booking : due) {
            try {
                MeetingType meetingType = meetingTypeService.getByIdOrThrow(booking.getMeetingTypeId());
                emailService.sendBookingReminder(booking, meetingType, "tomorrow");
                booking.setReminder24hSentAt(LocalDateTime.now());
                bookingRepository.save(booking);
            } catch (Exception e) {
                log.error("Failed to send 24h reminder for booking {}: {}", booking.getBookingNumber(), e.getMessage());
            }
        }
    }

    /** Every 5 minutes: send the 1h-ahead reminder. */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void send1hReminders() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        var due = bookingRepository.findDue1hReminders(now.plusMinutes(50), now.plusMinutes(70));
        for (Booking booking : due) {
            try {
                MeetingType meetingType = meetingTypeService.getByIdOrThrow(booking.getMeetingTypeId());
                emailService.sendBookingReminder(booking, meetingType, "in about an hour");
                booking.setReminder1hSentAt(LocalDateTime.now());
                bookingRepository.save(booking);
            } catch (Exception e) {
                log.error("Failed to send 1h reminder for booking {}: {}", booking.getBookingNumber(), e.getMessage());
            }
        }
    }

    /** Every 10 minutes: release slots held for a paid booking whose payment never
     *  completed within its hold window (feature 21/41), so they go back into the
     *  availability pool instead of phantom-blocking the calendar forever. */
    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void releaseExpiredHolds() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        var expired = bookingRepository.findByPaymentStatusAndHoldExpiresAtBefore(BookingPaymentStatus.PENDING, now);
        for (Booking booking : expired) {
            booking.setPaymentStatus(BookingPaymentStatus.FAILED);
            booking.setStatus(com.neelastack.entity.BookingStatus.CANCELLED);
            booking.setCancelReason("Payment window expired");
            booking.setCancelledAt(LocalDateTime.now());
            bookingRepository.save(booking);
            log.info("Released expired payment hold for booking {}", booking.getBookingNumber());
        }
    }
}
