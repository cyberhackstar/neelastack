package com.neelastack.service;

import com.neelastack.dto.booking.BookingFunnelStatsDto;
import com.neelastack.dto.booking.RevenueBySourceDto;
import com.neelastack.entity.Booking;
import com.neelastack.entity.LeadTier;
import com.neelastack.repository.BookingRepository;
import com.neelastack.repository.EngagementRepository;
import com.neelastack.repository.InquiryRepository;
import com.neelastack.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Inquiry -> booking -> attendance -> proposal -> client funnel (master prompt
 * section 29), and revenue-by-UTM-source attribution (section 30/31). Built entirely
 * from data already collected elsewhere (Inquiry.leadTier/utm*, Booking, Quotation,
 * Engagement) -- no new tracking pipeline, just a read-side rollup.
 */
@Service
@RequiredArgsConstructor
public class BookingAnalyticsService {

    private final InquiryRepository inquiryRepository;
    private final BookingRepository bookingRepository;
    private final QuotationRepository quotationRepository;
    private final EngagementRepository engagementRepository;

    @Transactional(readOnly = true)
    public BookingFunnelStatsDto funnel(LocalDateTime from, LocalDateTime to) {
        long inquiries = inquiryRepository.countByCreatedAtBetween(from, to);
        long qualified = inquiryRepository.countByCreatedAtBetweenAndLeadTierNot(from, to, LeadTier.NURTURE);

        OffsetDateTime fromOffset = from.atOffset(ZoneOffset.UTC);
        OffsetDateTime toOffset = to.atOffset(ZoneOffset.UTC);
        List<Booking> bookingsInRange = bookingRepository.findLiveBetween(fromOffset, toOffset);
        long booked = bookingsInRange.size();
        long attended = (long) bookingsInRange.stream()
                .filter(b -> b.getStatus().name().equals("COMPLETED")).count();

        long proposalsSent = quotationRepository.countBySentAtBetween(from, to);
        long clientsWon = engagementRepository.countByCreatedAtBetween(from, to);

        return BookingFunnelStatsDto.builder()
                .inquiries(inquiries)
                .qualifiedInquiries(qualified)
                .booked(booked)
                .attended(attended)
                .proposalsSent(proposalsSent)
                .clientsWon(clientsWon)
                .bookingRate(rate(booked, inquiries))
                .attendanceRate(rate(attended, booked))
                .proposalRate(rate(proposalsSent, attended))
                .closeRate(rate(clientsWon, proposalsSent))
                .build();
    }

    @Transactional(readOnly = true)
    public List<RevenueBySourceDto> revenueBySource(LocalDateTime from, LocalDateTime to) {
        OffsetDateTime fromOffset = from.atOffset(ZoneOffset.UTC);
        OffsetDateTime toOffset = to.atOffset(ZoneOffset.UTC);
        List<Booking> bookings = bookingRepository.findLiveBetween(fromOffset, toOffset);

        Map<String, List<Booking>> bySource = bookings.stream()
                .collect(Collectors.groupingBy(b -> {
                    if (b.getUtmSource() != null && !b.getUtmSource().isBlank()) return b.getUtmSource();
                    if (b.getSource() != null && !b.getSource().isBlank()) return b.getSource();
                    return "direct";
                }));

        return bySource.entrySet().stream()
                .map(e -> RevenueBySourceDto.builder()
                        .source(e.getKey())
                        .bookingCount(e.getValue().size())
                        .revenue(e.getValue().stream()
                                .map(b -> b.getPrice() == null ? BigDecimal.ZERO : b.getPrice())
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                        .build())
                .sorted(Comparator.comparing(RevenueBySourceDto::revenue).reversed())
                .toList();
    }

    private Double rate(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
        }
        return Math.round((numerator * 10000.0 / denominator)) / 100.0;
    }
}
